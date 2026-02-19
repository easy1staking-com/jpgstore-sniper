use aiken/collection/dict
use aiken/collection/list
use aiken/math.{max}
use aiken/math/rational
use cardano/address.{Address, Inline, Script, VerificationKey}
use cardano/assets.{PolicyId}
use cardano/transaction.{
  InlineDatum, Input, NoDatum, Output, OutputReference, Transaction,
}
use sniper/types.{
  Cancel, Execute, SettingsDatum, SnipeDatum, SpendRedeemer,
  percentage_denominator, settings_nft_name,
}
use sniper/utils.{compute_output_tag, signed_by}

/// Snipe Escrow Spending Validator
///
/// Parameterized by listing_nft_policy_id. Holds locked ADA for snipe orders.
///
/// Execute redeemer checks:
///   a. Listing NFT is in the consumed UTxO
///   b. Output to nft_destination contains >= 1 token under target policy_id,
///      with inline datum tag = hash(snipe_utxo_ref)
///   c. If protocol_fee > 0: output to protocol_treasury >= protocol_fee,
///      with inline datum tag = hash(snipe_utxo_ref)
///   d. Listing NFT is burned (prevents reuse with bogus fees)
///
/// Cancel redeemer checks:
///   - Signed by owner_pkh
///   - Listing NFT is burned (prevents reuse with bogus fees)
validator policy_snipe(settings_nft_policy_id: PolicyId) {
  mint(_redeemer: Data, policy_id: PolicyId, tx: Transaction) {
    let mint_dict = assets.to_dict(tx.mint)
    expect dict.size(mint_dict) == 1
    expect Some(minted_tokens) = dict.get(mint_dict, policy_id)
    expect dict.size(minted_tokens) == 1
    expect Some(num_minted_nft) = dict.get(minted_tokens, #"")

    if num_minted_nft > 0 {
      // 1. Find settings reference input (contains settings NFT)
      expect Some(settings_ref_input) =
        list.find(
          tx.reference_inputs,
          fn(input) {
            assets.has_nft(
              input.output.value,
              settings_nft_policy_id,
              settings_nft_name,
            )
          },
        )
      expect InlineDatum(settings_data) = settings_ref_input.output.datum
      expect settings: SettingsDatum = settings_data

      let expected_nft_value = assets.from_asset(policy_id, #"", 1)

      let expected_num_nft_minted =
        list.foldl(
          tx.outputs,
          0,
          fn(output, acc) {
            when output.address.payment_credential is {
              Script(script_hash) ->
                if script_hash == policy_id {
                  trace @"Found output with correct policy: "

                  expect Some(stake_credentials) =
                    output.address.stake_credential
                  expect Inline(stake_cred) = stake_credentials
                  expect stake_cred == settings.stake_credential

                  // Beside ada, just exactly 1 NFT under the policy
                  expect
                    assets.without_lovelace(output.value) == expected_nft_value

                  expect InlineDatum(output_datum) = output.datum
                  expect snipe_datum: SnipeDatum = output_datum

                  let (protocol_fees, operator_fees) =
                    calculate_fees(settings, snipe_datum)
                  let expected_min_lovelace_locked =
                    snipe_datum.max_price + protocol_fees + operator_fees + settings.tx_fee_budget

                  trace @"snipe_datum.protocol_fee: ": snipe_datum.protocol_fee
                  trace @"protocol_fees: ": protocol_fees

                  expect snipe_datum.protocol_fee == protocol_fees
                  expect
                    assets.lovelace_of(output.value) >= expected_min_lovelace_locked

                  acc + 1
                } else {
                  acc
                }
              _ -> acc
            }
          },
        )

      (num_minted_nft == expected_num_nft_minted)?
    } else {
      // if burning, we're spending the utxo (either executing the snipe or cancelling it), so we defer to spend redeemer for checks
      True
    }
  }

  spend(
    datum: Option<SnipeDatum>,
    redeemer: SpendRedeemer,
    own_ref: OutputReference,
    tx: Transaction,
  ) {
    // The common part is just counting the spend addresses. and negate the minting.
    // Then each spend ensure the logic holds (if cancel just signed by owner, if execute check the outputs, they all check the burn)
    // We could make that only early input performs burn check?

    // Find own input
    expect Some(own_input) =
      list.find(tx.inputs, fn(input) { input.output_reference == own_ref })

    // Get Script Hash
    expect Script(escrow_script_hash) =
      own_input.output.address.payment_credential

    // Select inputs for this contract and do generic part (i.e. if there's an NFT guarding the utxo, it must be burned)
    let amount_nft_to_burn =
      list.foldl(
        tx.inputs,
        0,
        fn(input, acc) {
          when input.output.address.payment_credential is {
            Script(script_hash) ->
              if script_hash == escrow_script_hash {
                // If there's more than 1 input from the same script, fail (could be used to bypass the burn check by having one input with the NFT and another without and not burning)
                if assets.has_nft(input.output.value, escrow_script_hash, #"") {
                  acc + 1
                } else {
                  acc
                }
              } else {
                acc
              }
            _ -> acc
          }
        },
      )

    // Ensure all the NFT are burned as part of this tx. This will prevent free floating unauthorised NFTs
    let expected_burned_amount =
      assets.from_asset(escrow_script_hash, #"", -1 * amount_nft_to_burn)

    expect tx.mint == expected_burned_amount

    expect Some(snipe_datum) = datum

    when redeemer is {
      Execute -> {
        expect assets.has_nft(own_input.output.value, escrow_script_hash, #"")

        // Ensure the own input has the NFT
        // Get PRotocol Settings from reference input
        expect Some(settings_ref_input) =
          list.find(
            tx.reference_inputs,
            fn(input) {
              assets.has_nft(
                input.output.value,
                settings_nft_policy_id,
                settings_nft_name,
              )
            },
          )
        expect InlineDatum(settings_data) = settings_ref_input.output.datum
        expect settings: SettingsDatum = settings_data

        // Compute tag for double satisfaction prevention
        let tag = compute_output_tag(own_ref)
        let expected_datum = InlineDatum(tag)

        expect (protocol_output_opt, Some(nft_destination_output)) =
          list.foldl(
            tx.outputs,
            (None, None),
            fn(output, acc) {
              trace @"output"
              if output.datum == expected_datum {
                trace @"matching datum"
                if output.address == snipe_datum.nft_destination {
                  trace @"Found output to destination with correct tag"
                  (acc.1st, Some(output))
                } else if output.address == settings.protocol_treasury {
                  trace @"Found output to treasury with correct tag"
                  (Some(output), acc.2nd)
                } else {
                  acc
                }
              } else {
                acc
              }
            },
          )

        let valid_protocol_output =
          when protocol_output_opt is {
            Some(protocol_output) ->
              assets.lovelace_of(protocol_output.value) >= snipe_datum.protocol_fee
            _ -> snipe_datum.protocol_fee == 0
          }

        // if protocol fee is 0, we don't need the output
        and {
          assets.has_any_nft(
            nft_destination_output.value,
            snipe_datum.policy_id,
          ),
          valid_protocol_output,
        }
      }

      Cancel ->
        // In case of cancel all I need to check is whether the tx is signed by the owner.
        signed_by(tx, snipe_datum.owner_pkh)
    }
  }

  else(_) {
    fail
  }
}

pub fn calculate_fees(
  settings: SettingsDatum,
  snipe_datum: SnipeDatum,
) -> (Int, Int) {
  let max_price = rational.from_int(snipe_datum.max_price)
  let protocol_fees =
    if settings.protocol_fee_pct == 0 {
      math.max(0, settings.min_protocol_fee)
    } else {
      expect Some(protocol_fees_pct) =
        rational.new(settings.protocol_fee_pct, percentage_denominator)
      let protocol_fees =
        max_price |> rational.mul(protocol_fees_pct) |> rational.ceil()
      math.max(protocol_fees, settings.min_protocol_fee)
    }
  expect Some(operator_fees_pct) =
    rational.new(settings.operator_fee_pct, percentage_denominator)

  let operator_fees =
    max_price |> rational.mul(operator_fees_pct) |> rational.ceil()
  (protocol_fees, operator_fees)
}

// ============================================================================
// Tests
// ============================================================================

const test_settings_nft_policy =
  #"00000000000000000000000000000000000000000000000000000022"

const test_listing_policy =
  #"00000000000000000000000000000000000000000000000000000044"

const test_escrow_script_hash =
  #"00000000000000000000000000000000000000000000000000000055"

const test_user_pkh =
  #"00000000000000000000000000000000000000000000000000000001"

const test_treasury_pkh =
  #"00000000000000000000000000000000000000000000000000000033"

const test_stake_pkh =
  #"00000000000000000000000000000000000000000000000000000077"

const test_consumed_tx_hash =
  #"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

fn test_treasury_address() -> Address {
  Address {
    payment_credential: VerificationKey(test_treasury_pkh),
    stake_credential: None,
  }
}

fn test_settings() -> SettingsDatum {
  SettingsDatum {
    operator_fee_pct: 2,
    protocol_fee_pct: 1,
    min_operator_fee: 1_000_000,
    min_protocol_fee: 1_000_000,
    protocol_treasury: test_treasury_address(),
    stake_credential: VerificationKey(test_stake_pkh),
    tx_fee_budget: 3_000_000,
    admin_pkh: test_user_pkh,
  }
}

// max_price=100 ADA → op_fee=max(2_000_000, 1_000_000)=2_000_000, proto=max(1_000_000, 1_000_000)=1_000_000
fn test_snipe_datum() -> SnipeDatum {
  SnipeDatum {
    owner_pkh: test_user_pkh,
    nft_destination: Address {
      payment_credential: VerificationKey(test_user_pkh),
      stake_credential: None,
    },
    policy_id: #"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
    max_price: 100_000_000,
    protocol_fee: 1_000_000,
  }
}

fn test_escrow_address() -> Address {
  Address {
    payment_credential: Script(test_listing_policy),
    stake_credential: Some(Inline(VerificationKey(test_stake_pkh))),
  }
}

/// Locked = 100 + 2 + 1 + 3 = 106 ADA
fn test_locked_lovelace(
  protocol_settings: SettingsDatum,
  snipe_datum: SnipeDatum,
) -> Int {
  snipe_datum.max_price + max(
    snipe_datum.max_price * protocol_settings.operator_fee_pct / percentage_denominator,
    protocol_settings.min_operator_fee,
  ) + max(
    snipe_datum.max_price * protocol_settings.protocol_fee_pct / percentage_denominator,
    protocol_settings.min_protocol_fee,
  ) + protocol_settings.tx_fee_budget
}

fn settings_ref_input() -> Input {
  Input {
    output_reference: OutputReference {
      transaction_id: #"0000000000000000000000000000000000000000000000000000000000000099",
      output_index: 0,
    },
    output: Output {
      address: Address {
        payment_credential: Script(
          #"00000000000000000000000000000000000000000000000000000088",
        ),
        stake_credential: None,
      },
      value: assets.from_lovelace(2_000_000)
        |> assets.add(test_settings_nft_policy, settings_nft_name, 1),
      datum: InlineDatum(test_settings()),
      reference_script: None,
    },
  }
}

fn consumed_user_input() -> Input {
  Input {
    output_reference: OutputReference {
      transaction_id: test_consumed_tx_hash,
      output_index: 0,
    },
    output: Output {
      address: Address {
        payment_credential: VerificationKey(test_user_pkh),
        stake_credential: None,
      },
      value: assets.from_lovelace(200_000_000),
      datum: NoDatum,
      reference_script: None,
    },
  }
}

fn escrow_output_with_nft() -> Output {
  Output {
    address: test_escrow_address(),
    value: assets.from_lovelace(
      test_locked_lovelace(test_settings(), test_snipe_datum()),
    )
      |> assets.add(test_listing_policy, #"", 1),
    datum: InlineDatum(test_snipe_datum()),
    reference_script: None,
  }
}

test listing_nft_mint_success() {
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [consumed_user_input()],
      reference_inputs: [settings_ref_input()],
      outputs: [escrow_output_with_nft()],
      mint: assets.from_asset(test_listing_policy, #"", 1),
    }
  policy_snipe.mint(test_settings_nft_policy, Void, test_listing_policy, tx)
}

test listing_nft_mint_fails_without_settings_ref() fail {
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [consumed_user_input()],
      reference_inputs: [],
      outputs: [escrow_output_with_nft()],
      mint: assets.from_asset(test_listing_policy, #"", 1),
    }
  policy_snipe.mint(test_settings_nft_policy, Void, test_listing_policy, tx)
}

test listing_nft_mint_fails_underfunded() fail {
  let underfunded_output =
    Output {
      address: test_escrow_address(),
      value: assets.from_lovelace(50_000_000)
        |> assets.add(test_listing_policy, #"", 1),
      datum: InlineDatum(test_snipe_datum()),
      reference_script: None,
    }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [consumed_user_input()],
      reference_inputs: [settings_ref_input()],
      outputs: [underfunded_output],
      mint: assets.from_asset(test_listing_policy, #"", 1),
    }
  policy_snipe.mint(test_settings_nft_policy, Void, test_listing_policy, tx)
}

test listing_nft_mint_fails_wrong_stake_credential() fail {
  let bad_address =
    Address {
      payment_credential: Script(test_escrow_script_hash),
      stake_credential: Some(Inline(VerificationKey(test_user_pkh))),
    }
  let bad_output = Output { ..escrow_output_with_nft(), address: bad_address }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [consumed_user_input()],
      reference_inputs: [settings_ref_input()],
      outputs: [bad_output],
      mint: assets.from_asset(test_listing_policy, #"", 1),
    }
  policy_snipe.mint(test_settings_nft_policy, Void, test_listing_policy, tx)
}

test listing_nft_mint_fails_not_script_address() fail {
  let pkh_address =
    Address {
      payment_credential: VerificationKey(test_user_pkh),
      stake_credential: Some(Inline(VerificationKey(test_stake_pkh))),
    }
  let bad_output = Output { ..escrow_output_with_nft(), address: pkh_address }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [consumed_user_input()],
      reference_inputs: [settings_ref_input()],
      outputs: [bad_output],
      mint: assets.from_asset(test_listing_policy, #"", 1),
    }
  policy_snipe.mint(test_settings_nft_policy, Void, test_listing_policy, tx)
}

test listing_nft_mint_fails_qty_2() fail {
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [consumed_user_input()],
      reference_inputs: [settings_ref_input()],
      outputs: [escrow_output_with_nft()],
      mint: assets.from_asset(test_listing_policy, #"", 2),
    }
  policy_snipe.mint(test_settings_nft_policy, Void, test_listing_policy, tx)
}

test listing_nft_burn_success() {
  let tx =
    Transaction {
      ..transaction.placeholder,
      mint: assets.from_asset(test_listing_policy, #"", -1),
    }
  policy_snipe.mint(test_settings_nft_policy, Void, test_listing_policy, tx)
}

test listing_nft_burn_fails_positive() fail {
  let tx =
    Transaction {
      ..transaction.placeholder,
      mint: assets.from_asset(test_listing_policy, #"", 1),
    }
  policy_snipe.mint(test_settings_nft_policy, Void, test_listing_policy, tx)
}

// Test min_fee enforcement: small max_price where min_fee > calculated pct
test listing_nft_mint_success_min_fee_kicks_in() {
  // max_price=5 ADA: pct would give op=0.1 ADA, proto=0.05 ADA
  // but min fees are 1 ADA each, so fees = 1 + 1 = 2 ADA
  // locked = 5 + 1 + 1 + 3 = 10 ADA
  let small_datum =
    SnipeDatum {
      owner_pkh: test_user_pkh,
      nft_destination: Address {
        payment_credential: VerificationKey(test_user_pkh),
        stake_credential: None,
      },
      policy_id: #"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
      max_price: 5_000_000,
      protocol_fee: 1_000_000,
    }
  let small_output =
    Output {
      address: test_escrow_address(),
      value: assets.from_lovelace(10_000_000)
        |> assets.add(test_listing_policy, #"", 1),
      datum: InlineDatum(small_datum),
      reference_script: None,
    }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [consumed_user_input()],
      reference_inputs: [settings_ref_input()],
      outputs: [small_output],
      mint: assets.from_asset(test_listing_policy, #"", 1),
    }
  policy_snipe.mint(test_settings_nft_policy, Void, test_listing_policy, tx)
}

// Test zero protocol fee promotion
test listing_nft_mint_success_zero_protocol_fee() {
  // Settings with 0% protocol fee and 0 min
  let promo_settings =
    SettingsDatum {
      ..test_settings(),
      protocol_fee_pct: 0,
      min_protocol_fee: 0,
    }
  let promo_ref_input =
    Input {
      output_reference: OutputReference {
        transaction_id: #"0000000000000000000000000000000000000000000000000000000000000099",
        output_index: 0,
      },
      output: Output {
        address: Address {
          payment_credential: Script(
            #"00000000000000000000000000000000000000000000000000000088",
          ),
          stake_credential: None,
        },
        value: assets.from_lovelace(2_000_000)
          |> assets.add(test_settings_nft_policy, "settings", 1),
        datum: InlineDatum(promo_settings),
        reference_script: None,
      },
    }
  // locked = 100 + 2 + 0 + 3 = 105 ADA
  let promo_datum = SnipeDatum { ..test_snipe_datum(), protocol_fee: 0 }
  let promo_output =
    Output {
      address: test_escrow_address(),
      value: assets.from_lovelace(
        test_locked_lovelace(promo_settings, promo_datum),
      )
        |> assets.add(test_listing_policy, #"", 1),
      datum: InlineDatum(promo_datum),
      reference_script: None,
    }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [consumed_user_input()],
      reference_inputs: [promo_ref_input],
      outputs: [promo_output],
      mint: assets.from_asset(test_listing_policy, #"", 1),
    }
  policy_snipe.mint(test_settings_nft_policy, Void, test_listing_policy, tx)
}

// ============================================================================
// Tests
// ============================================================================

const test_target_policy =
  #"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

const test_owner_pkh =
  #"00000000000000000000000000000000000000000000000000000001"

fn test_snipe_oref() -> OutputReference {
  OutputReference {
    transaction_id: #"0000000000000000000000000000000000000000000000000000000000000001",
    output_index: 0,
  }
}

fn test_owner_address() -> Address {
  Address {
    payment_credential: VerificationKey(test_user_pkh),
    stake_credential: None,
  }
}

fn escrow_input(oref: OutputReference) -> Input {
  Input {
    output_reference: oref,
    output: Output {
      address: Address {
        payment_credential: Script(test_listing_policy),
        stake_credential: None,
      },
      value: assets.from_lovelace(106_000_000)
        |> assets.add(test_listing_policy, #"", 1),
      datum: InlineDatum(test_snipe_datum()),
      reference_script: None,
    },
  }
}

fn nft_delivery_output(tag: ByteArray) -> Output {
  Output {
    address: test_owner_address(),
    value: assets.from_lovelace(2_000_000)
      |> assets.add(test_target_policy, "cool_nft_001", 1),
    datum: InlineDatum(tag),
    reference_script: None,
  }
}

fn protocol_fee_output(tag: ByteArray) -> Output {
  Output {
    address: test_treasury_address(),
    value: assets.from_lovelace(1_000_000),
    datum: InlineDatum(tag),
    reference_script: None,
  }
}

// === Execute Tests ===

test execute_success() {
  let oref = test_snipe_oref()
  let tag = compute_output_tag(oref)
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [escrow_input(oref)],
      outputs: [nft_delivery_output(tag), protocol_fee_output(tag)],
      mint: assets.from_asset(test_listing_policy, #"", -1),
      reference_inputs: [settings_ref_input()],
    }
  policy_snipe.spend(
    test_settings_nft_policy,
    Some(test_snipe_datum()),
    Execute,
    oref,
    tx,
  )
}

test execute_fails_without_nft_delivery() fail {
  let oref = test_snipe_oref()
  let tag = compute_output_tag(oref)
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [escrow_input(oref)],
      outputs: [protocol_fee_output(tag)],
      mint: assets.from_asset(test_listing_policy, #"", -1),
      reference_inputs: [settings_ref_input()],
    }
  policy_snipe.spend(
    test_settings_nft_policy,
    Some(test_snipe_datum()),
    Execute,
    oref,
    tx,
  )
}

test execute_fails_without_protocol_fee() fail {
  let oref = test_snipe_oref()
  let tag = compute_output_tag(oref)
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [escrow_input(oref)],
      outputs: [nft_delivery_output(tag)],
      mint: assets.from_asset(test_listing_policy, #"", -1),
      reference_inputs: [settings_ref_input()],
    }
  policy_snipe.spend(
    test_settings_nft_policy,
    Some(test_snipe_datum()),
    Execute,
    oref,
    tx,
  )
}

test execute_fails_without_listing_nft_burn() fail {
  let oref = test_snipe_oref()
  let tag = compute_output_tag(oref)
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [escrow_input(oref)],
      outputs: [nft_delivery_output(tag), protocol_fee_output(tag)],
      mint: assets.zero,
      reference_inputs: [settings_ref_input()],
    }
  policy_snipe.spend(
    test_settings_nft_policy,
    Some(test_snipe_datum()),
    Execute,
    oref,
    tx,
  )
}

// A bare UTxO at the escrow address (without listing NFT) cannot be spent
// via Execute — the listing NFT is required in own input.
test execute_bare_utxo_without_listing_nft_fails() fail {
  let oref = test_snipe_oref()
  let tag = compute_output_tag(oref)
  let input_no_nft =
    Input {
      output_reference: oref,
      output: Output {
        address: Address {
          payment_credential: Script(test_listing_policy),
          stake_credential: None,
        },
        value: assets.from_lovelace(106_000_000),
        datum: InlineDatum(test_snipe_datum()),
        reference_script: None,
      },
    }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [input_no_nft],
      outputs: [nft_delivery_output(tag), protocol_fee_output(tag)],
      mint: assets.zero,
      reference_inputs: [settings_ref_input()],
    }
  policy_snipe.spend(
    test_settings_nft_policy,
    Some(test_snipe_datum()),
    Execute,
    oref,
    tx,
  )
}

// When a bare UTxO exists alongside a proper NFT-bearing input, the burn
// must still account for all NFTs. This tests that the bare input + proper
// burn of 0 still fails when there's actually an NFT in another input.
test execute_bare_utxo_fails_if_other_inputs_have_nft() fail {
  let oref = test_snipe_oref()
  let tag = compute_output_tag(oref)
  let input_no_nft =
    Input {
      output_reference: oref,
      output: Output {
        address: Address {
          payment_credential: Script(test_listing_policy),
          stake_credential: None,
        },
        value: assets.from_lovelace(106_000_000),
        datum: InlineDatum(test_snipe_datum()),
        reference_script: None,
      },
    }
  let other_oref =
    OutputReference {
      transaction_id: #"0000000000000000000000000000000000000000000000000000000000000002",
      output_index: 0,
    }
  let input_with_nft = escrow_input(other_oref)
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [input_no_nft, input_with_nft],
      outputs: [nft_delivery_output(tag), protocol_fee_output(tag)],
      // Only burning 0, but there's 1 NFT in the other input
      mint: assets.zero,
      reference_inputs: [settings_ref_input()],
    }
  policy_snipe.spend(
    test_settings_nft_policy,
    Some(test_snipe_datum()),
    Execute,
    oref,
    tx,
  )
}

test execute_fails_wrong_tag_on_nft_output() fail {
  let oref = test_snipe_oref()
  let tag = compute_output_tag(oref)
  let wrong_tag =
    #"deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [escrow_input(oref)],
      outputs: [nft_delivery_output(wrong_tag), protocol_fee_output(tag)],
      mint: assets.from_asset(test_listing_policy, #"", -1),
      reference_inputs: [settings_ref_input()],
    }
  policy_snipe.spend(
    test_settings_nft_policy,
    Some(test_snipe_datum()),
    Execute,
    oref,
    tx,
  )
}

test execute_fails_insufficient_protocol_fee() fail {
  let oref = test_snipe_oref()
  let tag = compute_output_tag(oref)
  let low_fee_output =
    Output {
      address: test_treasury_address(),
      value: assets.from_lovelace(500_000),
      datum: InlineDatum(tag),
      reference_script: None,
    }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [escrow_input(oref)],
      outputs: [nft_delivery_output(tag), low_fee_output],
      mint: assets.from_asset(test_listing_policy, #"", -1),
      reference_inputs: [settings_ref_input()],
    }
  policy_snipe.spend(
    test_settings_nft_policy,
    Some(test_snipe_datum()),
    Execute,
    oref,
    tx,
  )
}

// === Zero Protocol Fee (Promotion) ===

test execute_success_zero_protocol_fee() {
  let oref = test_snipe_oref()
  let tag = compute_output_tag(oref)
  let promo_datum = SnipeDatum { ..test_snipe_datum(), protocol_fee: 0 }
  let promo_input =
    Input {
      output_reference: oref,
      output: Output {
        address: Address {
          payment_credential: Script(test_escrow_script_hash),
          stake_credential: None,
        },
        value: assets.from_lovelace(105_000_000)
          |> assets.add(test_escrow_script_hash, #"", 1),
        datum: InlineDatum(promo_datum),
        reference_script: None,
      },
    }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [promo_input],
      outputs: [nft_delivery_output(tag)],
      mint: assets.from_asset(test_escrow_script_hash, #"", -1),
      reference_inputs: [settings_ref_input()],
    }
  policy_snipe.spend(
    test_settings_nft_policy,
    Some(promo_datum),
    Execute,
    oref,
    tx,
  )
}

// === Cancel Tests ===

test cancel_success() {
  let oref = test_snipe_oref()
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [escrow_input(oref)],
      extra_signatories: [test_owner_pkh],
      mint: assets.from_asset(test_listing_policy, #"", -1),
    }
  policy_snipe.spend(
    test_settings_nft_policy,
    Some(test_snipe_datum()),
    Cancel,
    oref,
    tx,
  )
}

test cancel_fails_without_owner_sig() fail {
  let oref = test_snipe_oref()
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [escrow_input(oref)],
      extra_signatories: [],
      mint: assets.from_asset(test_listing_policy, #"", -1),
    }
  policy_snipe.spend(
    test_settings_nft_policy,
    Some(test_snipe_datum()),
    Cancel,
    oref,
    tx,
  )
}

test cancel_fails_without_burn() fail {
  let oref = test_snipe_oref()
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [escrow_input(oref)],
      extra_signatories: [test_owner_pkh],
      mint: assets.zero,
    }
  policy_snipe.spend(
    test_settings_nft_policy,
    Some(test_snipe_datum()),
    Cancel,
    oref,
    tx,
  )
}

test cancel_fails_wrong_signer() fail {
  let oref = test_snipe_oref()
  let wrong_pkh = #"ffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [escrow_input(oref)],
      extra_signatories: [wrong_pkh],
      mint: assets.from_asset(test_listing_policy, #"", -1),
    }
  policy_snipe.spend(
    test_settings_nft_policy,
    Some(test_snipe_datum()),
    Cancel,
    oref,
    tx,
  )
}

// === Cancel — None Datum ===

test cancel_fails_with_none_datum() fail {
  let oref = test_snipe_oref()
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [escrow_input(oref)],
      extra_signatories: [test_owner_pkh],
      mint: assets.from_asset(test_listing_policy, #"", -1),
    }
  policy_snipe.spend(test_settings_nft_policy, None, Cancel, oref, tx)
}

// === Execute — Wrong NFT Policy in Delivery ===

test execute_fails_wrong_nft_policy_in_delivery() fail {
  let oref = test_snipe_oref()
  let tag = compute_output_tag(oref)
  let wrong_policy = #"cccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
  let wrong_nft_output =
    Output {
      address: test_owner_address(),
      value: assets.from_lovelace(2_000_000)
        |> assets.add(wrong_policy, "cool_nft_001", 1),
      datum: InlineDatum(tag),
      reference_script: None,
    }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [escrow_input(oref)],
      outputs: [wrong_nft_output, protocol_fee_output(tag)],
      mint: assets.from_asset(test_listing_policy, #"", -1),
      reference_inputs: [settings_ref_input()],
    }
  policy_snipe.spend(
    test_settings_nft_policy,
    Some(test_snipe_datum()),
    Execute,
    oref,
    tx,
  )
}

// === Execute — Wrong Destination Address ===

test execute_fails_wrong_destination_address() fail {
  let oref = test_snipe_oref()
  let tag = compute_output_tag(oref)
  let wrong_address =
    Address {
      payment_credential: VerificationKey(test_treasury_pkh),
      stake_credential: None,
    }
  let wrong_dest_output =
    Output {
      address: wrong_address,
      value: assets.from_lovelace(2_000_000)
        |> assets.add(test_target_policy, "cool_nft_001", 1),
      datum: InlineDatum(tag),
      reference_script: None,
    }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [escrow_input(oref)],
      outputs: [wrong_dest_output, protocol_fee_output(tag)],
      mint: assets.from_asset(test_listing_policy, #"", -1),
      reference_inputs: [settings_ref_input()],
    }
  policy_snipe.spend(
    test_settings_nft_policy,
    Some(test_snipe_datum()),
    Execute,
    oref,
    tx,
  )
}

// === Execute — Double Satisfaction Prevention ===
// Two escrow inputs sharing one delivery output should fail because
// each input's tag is unique (derived from its own oref).

test execute_fails_double_satisfaction() fail {
  let oref1 = test_snipe_oref()
  let oref2 =
    OutputReference {
      transaction_id: #"0000000000000000000000000000000000000000000000000000000000000002",
      output_index: 0,
    }
  // Use oref2's tag for the outputs — oref1 should fail
  let tag2 = compute_output_tag(oref2)
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [escrow_input(oref1), escrow_input(oref2)],
      outputs: [nft_delivery_output(tag2), protocol_fee_output(tag2)],
      mint: assets.from_asset(test_listing_policy, #"", -2),
      reference_inputs: [settings_ref_input()],
    }
  // Spend from oref1's perspective — tag won't match
  policy_snipe.spend(
    test_settings_nft_policy,
    Some(test_snipe_datum()),
    Execute,
    oref1,
    tx,
  )
}

// === Execute — Protocol Fee Bypass Vulnerability ===
// SECURITY: snipe_datum.protocol_fee=0 is NOT validated against settings.
// This test documents that the protocol fee can be bypassed.

test execute_succeeds_with_protocol_fee_bypass() {
  let oref = test_snipe_oref()
  let tag = compute_output_tag(oref)
  // Datum with protocol_fee: 0, even though settings require > 0
  let bypass_datum = SnipeDatum { ..test_snipe_datum(), protocol_fee: 0 }
  let bypass_input =
    Input {
      output_reference: oref,
      output: Output {
        address: Address {
          payment_credential: Script(test_listing_policy),
          stake_credential: None,
        },
        value: assets.from_lovelace(106_000_000)
          |> assets.add(test_listing_policy, #"", 1),
        datum: InlineDatum(bypass_datum),
        reference_script: None,
      },
    }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [bypass_input],
      // No protocol fee output — bypassed because datum says 0
      outputs: [nft_delivery_output(tag)],
      mint: assets.from_asset(test_listing_policy, #"", -1),
      reference_inputs: [settings_ref_input()],
    }
  policy_snipe.spend(
    test_settings_nft_policy,
    Some(bypass_datum),
    Execute,
    oref,
    tx,
  )
}

// === Mint — Stake Credential Enforced ===

test listing_nft_mint_fails_with_wrong_stake_credential_value() fail {
  let attacker_stake =
    #"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaabb"
  let bad_address =
    Address {
      payment_credential: Script(test_listing_policy),
      stake_credential: Some(Inline(VerificationKey(attacker_stake))),
    }
  let bad_output = Output { ..escrow_output_with_nft(), address: bad_address }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [consumed_user_input()],
      reference_inputs: [settings_ref_input()],
      outputs: [bad_output],
      mint: assets.from_asset(test_listing_policy, #"", 1),
    }
  policy_snipe.mint(test_settings_nft_policy, Void, test_listing_policy, tx)
}

// === Mint — No Stake Credential Rejected ===

test listing_nft_mint_fails_with_no_stake_credential() fail {
  let no_stake_address =
    Address {
      payment_credential: Script(test_listing_policy),
      stake_credential: None,
    }
  let output = Output { ..escrow_output_with_nft(), address: no_stake_address }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [consumed_user_input()],
      reference_inputs: [settings_ref_input()],
      outputs: [output],
      mint: assets.from_asset(test_listing_policy, #"", 1),
    }
  policy_snipe.mint(test_settings_nft_policy, Void, test_listing_policy, tx)
}

// === Mint — Multiple Outputs Each With 1 NFT ===
// If 2 NFTs are minted and distributed to 2 outputs, both must validate.

test listing_nft_mint_success_multiple_outputs() {
  let other_snipe =
    SnipeDatum {
      owner_pkh: test_user_pkh,
      nft_destination: Address {
        payment_credential: VerificationKey(test_user_pkh),
        stake_credential: None,
      },
      policy_id: #"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
      max_price: 50_000_000,
      protocol_fee: 1_000_000,
    }
  let output2 =
    Output {
      address: test_escrow_address(),
      value: assets.from_lovelace(
        test_locked_lovelace(test_settings(), other_snipe),
      )
        |> assets.add(test_listing_policy, #"", 1),
      datum: InlineDatum(other_snipe),
      reference_script: None,
    }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [consumed_user_input()],
      reference_inputs: [settings_ref_input()],
      outputs: [escrow_output_with_nft(), output2],
      mint: assets.from_asset(test_listing_policy, #"", 2),
    }
  policy_snipe.mint(test_settings_nft_policy, Void, test_listing_policy, tx)
}

// === Mint — 2 NFTs Minted But Only 1 Output ===

test listing_nft_mint_fails_qty_mismatch() fail {
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [consumed_user_input()],
      reference_inputs: [settings_ref_input()],
      outputs: [escrow_output_with_nft()],
      mint: assets.from_asset(test_listing_policy, #"", 2),
    }
  policy_snipe.mint(test_settings_nft_policy, Void, test_listing_policy, tx)
}

// === Mint — Extra Tokens in Escrow Output (Token Dust) ===

test listing_nft_mint_fails_extra_tokens_in_output() fail {
  let dust_policy = #"dddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
  let dusty_output =
    Output {
      address: test_escrow_address(),
      value: assets.from_lovelace(
        test_locked_lovelace(test_settings(), test_snipe_datum()),
      )
        |> assets.add(test_listing_policy, #"", 1)
        |> assets.add(dust_policy, "dust", 1),
      datum: InlineDatum(test_snipe_datum()),
      reference_script: None,
    }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [consumed_user_input()],
      reference_inputs: [settings_ref_input()],
      outputs: [dusty_output],
      mint: assets.from_asset(test_listing_policy, #"", 1),
    }
  policy_snipe.mint(test_settings_nft_policy, Void, test_listing_policy, tx)
}

// === Execute — Multiple Escrow Inputs Batch ===

test execute_success_batch_two_inputs() {
  let oref1 = test_snipe_oref()
  let oref2 =
    OutputReference {
      transaction_id: #"0000000000000000000000000000000000000000000000000000000000000002",
      output_index: 0,
    }
  let tag1 = compute_output_tag(oref1)
  let tag2 = compute_output_tag(oref2)
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [escrow_input(oref1), escrow_input(oref2)],
      outputs: [
        nft_delivery_output(tag1),
        protocol_fee_output(tag1),
        nft_delivery_output(tag2),
        protocol_fee_output(tag2),
      ],
      mint: assets.from_asset(test_listing_policy, #"", -2),
      reference_inputs: [settings_ref_input()],
    }
  // Verify from oref1's perspective
  policy_snipe.spend(
    test_settings_nft_policy,
    Some(test_snipe_datum()),
    Execute,
    oref1,
    tx,
  )
}

// === Execute — Missing Settings Reference Input ===

test execute_fails_without_settings_ref() fail {
  let oref = test_snipe_oref()
  let tag = compute_output_tag(oref)
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [escrow_input(oref)],
      outputs: [nft_delivery_output(tag), protocol_fee_output(tag)],
      mint: assets.from_asset(test_listing_policy, #"", -1),
      reference_inputs: [],
    }
  policy_snipe.spend(
    test_settings_nft_policy,
    Some(test_snipe_datum()),
    Execute,
    oref,
    tx,
  )
}
