use aiken/collection/dict
use aiken/collection/list
use aiken/primitive/bytearray
use cardano/address.{Address, Inline, Script, VerificationKey}
use cardano/assets.{PolicyId}
use cardano/transaction.{
  InlineDatum, Input, NoDatum, Output, OutputReference, Transaction,
}
use sniper/types.{Burn, Mint, MintRedeemer, SettingsDatum, SnipeDatum}
use sniper/utils.{calculate_fee, calculate_locked_amount}

/// Listing NFT Minting Policy (Shared)
///
/// Parameterized by settings_nft_policy_id. On Mint, validates:
///   1. Settings reference input is present (contains settings NFT)
///   2. Token name is derived from a consumed UTxO ref (uniqueness)
///   3. Exactly 1 token minted per name
///   4. Output at escrow script has correct inline SnipeDatum
///   5. Datum fees are correctly computed from settings (absolute amounts)
///   6. Output has correct stake credential (from settings)
///   7. Full locked amount is present in the output
///
/// On Burn: always allowed.
validator listing_nft(settings_nft_policy_id: PolicyId) {
  mint(redeemer: MintRedeemer, policy_id: PolicyId, tx: Transaction) {
    when redeemer is {
      Mint -> {
        // 1. Find settings reference input (contains settings NFT)
        expect Some(settings_ref_input) =
          list.find(
            tx.reference_inputs,
            fn(input) {
              assets.has_any_nft(input.output.value, settings_nft_policy_id)
            },
          )
        expect InlineDatum(settings_data) = settings_ref_input.output.datum
        expect settings: SettingsDatum = settings_data

        // 2. Get all tokens being minted under this policy
        let minted_tokens = assets.tokens(tx.mint, policy_id)

        // 3. For each minted token: validate uniqueness, quantity, and matching output
        dict.foldl(
          minted_tokens,
          True,
          fn(token_name, qty, acc) {
            // Each token must have quantity exactly 1
            let qty_ok = qty == 1

            // Token name must correspond to a consumed UTxO ref:
            // token_name = tx_hash ++ output_index (big-endian bytes)
            let name_from_input =
              list.any(
                tx.inputs,
                fn(input) {
                  let expected_name =
                    bytearray.concat(
                      input.output_reference.transaction_id,
                      bytearray.from_int_big_endian(
                        input.output_reference.output_index,
                        0,
                      ),
                    )
                  expected_name == token_name
                },
              )

            // Find the output that contains this listing NFT
            expect Some(escrow_output) =
              list.find(
                tx.outputs,
                fn(output) {
                  assets.quantity_of(output.value, policy_id, token_name) == 1
                },
              )

            // Output must be at a script address
            let is_script_address =
              when escrow_output.address.payment_credential is {
                Script(_) -> True
                _ -> False
              }

            // Output must have correct stake credential from settings
            let stake_ok =
              escrow_output.address.stake_credential == Some(
                Inline(settings.stake_credential),
              )

            // Output must have inline SnipeDatum
            expect InlineDatum(datum_data) = escrow_output.datum
            expect snipe_datum: SnipeDatum = datum_data

            // Compute expected absolute fees from settings
            let expected_op_fee =
              calculate_fee(
                snipe_datum.max_price,
                settings.operator_fee_pct,
                settings.min_operator_fee,
              )
            let expected_proto_fee =
              calculate_fee(
                snipe_datum.max_price,
                settings.protocol_fee_pct,
                settings.min_protocol_fee,
              )

            // Datum must store correct absolute fee amounts and treasury
            let fees_match =
              snipe_datum.operator_fee == expected_op_fee && snipe_datum.protocol_fee == expected_proto_fee && snipe_datum.protocol_treasury == settings.protocol_treasury && snipe_datum.tx_fee_budget == settings.tx_fee_budget

            // Full locked amount must be present
            let required_lovelace =
              calculate_locked_amount(
                snipe_datum.max_price,
                snipe_datum.operator_fee,
                snipe_datum.protocol_fee,
                snipe_datum.tx_fee_budget,
              )
            let locked_ok =
              assets.lovelace_of(escrow_output.value) >= required_lovelace

            acc && qty_ok && name_from_input && is_script_address && stake_ok && fees_match && locked_ok
          },
        )
      }

      Burn -> {
        // Burn: all quantities must be negative
        let minted_tokens = assets.tokens(tx.mint, policy_id)
        dict.foldl(minted_tokens, True, fn(_name, qty, acc) { acc && qty < 0 })
      }
    }
  }

  else(_) {
    fail
  }
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
    operator_fee_pct: 200,
    protocol_fee_pct: 100,
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
    operator_fee: 2_000_000,
    protocol_fee: 1_000_000,
    protocol_treasury: test_treasury_address(),
    tx_fee_budget: 3_000_000,
  }
}

fn test_token_name() -> ByteArray {
  bytearray.concat(test_consumed_tx_hash, bytearray.from_int_big_endian(0, 0))
}

fn test_escrow_address() -> Address {
  Address {
    payment_credential: Script(test_escrow_script_hash),
    stake_credential: Some(Inline(VerificationKey(test_stake_pkh))),
  }
}

/// Locked = 100 + 2 + 1 + 3 = 106 ADA
fn test_locked_lovelace() -> Int {
  106_000_000
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
        |> assets.add(test_settings_nft_policy, "settings", 1),
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
    value: assets.from_lovelace(test_locked_lovelace())
      |> assets.add(test_listing_policy, test_token_name(), 1),
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
      mint: assets.from_asset(test_listing_policy, test_token_name(), 1),
    }
  listing_nft.mint(test_settings_nft_policy, Mint, test_listing_policy, tx)
}

test listing_nft_mint_fails_without_settings_ref() fail {
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [consumed_user_input()],
      reference_inputs: [],
      outputs: [escrow_output_with_nft()],
      mint: assets.from_asset(test_listing_policy, test_token_name(), 1),
    }
  listing_nft.mint(test_settings_nft_policy, Mint, test_listing_policy, tx)
}

test listing_nft_mint_fails_wrong_fee_amount() fail {
  // Datum has wrong operator_fee (500_000 instead of 2_000_000)
  let bad_datum = SnipeDatum { ..test_snipe_datum(), operator_fee: 500_000 }
  let bad_output =
    Output { ..escrow_output_with_nft(), datum: InlineDatum(bad_datum) }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [consumed_user_input()],
      reference_inputs: [settings_ref_input()],
      outputs: [bad_output],
      mint: assets.from_asset(test_listing_policy, test_token_name(), 1),
    }
  listing_nft.mint(test_settings_nft_policy, Mint, test_listing_policy, tx)
}

test listing_nft_mint_fails_underfunded() fail {
  let underfunded_output =
    Output {
      address: test_escrow_address(),
      value: assets.from_lovelace(50_000_000)
        |> assets.add(test_listing_policy, test_token_name(), 1),
      datum: InlineDatum(test_snipe_datum()),
      reference_script: None,
    }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [consumed_user_input()],
      reference_inputs: [settings_ref_input()],
      outputs: [underfunded_output],
      mint: assets.from_asset(test_listing_policy, test_token_name(), 1),
    }
  listing_nft.mint(test_settings_nft_policy, Mint, test_listing_policy, tx)
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
      mint: assets.from_asset(test_listing_policy, test_token_name(), 1),
    }
  listing_nft.mint(test_settings_nft_policy, Mint, test_listing_policy, tx)
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
      mint: assets.from_asset(test_listing_policy, test_token_name(), 1),
    }
  listing_nft.mint(test_settings_nft_policy, Mint, test_listing_policy, tx)
}

test listing_nft_mint_fails_qty_2() fail {
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [consumed_user_input()],
      reference_inputs: [settings_ref_input()],
      outputs: [escrow_output_with_nft()],
      mint: assets.from_asset(test_listing_policy, test_token_name(), 2),
    }
  listing_nft.mint(test_settings_nft_policy, Mint, test_listing_policy, tx)
}

test listing_nft_burn_success() {
  let tx =
    Transaction {
      ..transaction.placeholder,
      mint: assets.from_asset(test_listing_policy, test_token_name(), -1),
    }
  listing_nft.mint(test_settings_nft_policy, Burn, test_listing_policy, tx)
}

test listing_nft_burn_fails_positive() fail {
  let tx =
    Transaction {
      ..transaction.placeholder,
      mint: assets.from_asset(test_listing_policy, test_token_name(), 1),
    }
  listing_nft.mint(test_settings_nft_policy, Burn, test_listing_policy, tx)
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
      operator_fee: 1_000_000,
      protocol_fee: 1_000_000,
      protocol_treasury: test_treasury_address(),
      tx_fee_budget: 3_000_000,
    }
  let small_output =
    Output {
      address: test_escrow_address(),
      value: assets.from_lovelace(10_000_000)
        |> assets.add(test_listing_policy, test_token_name(), 1),
      datum: InlineDatum(small_datum),
      reference_script: None,
    }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [consumed_user_input()],
      reference_inputs: [settings_ref_input()],
      outputs: [small_output],
      mint: assets.from_asset(test_listing_policy, test_token_name(), 1),
    }
  listing_nft.mint(test_settings_nft_policy, Mint, test_listing_policy, tx)
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
      value: assets.from_lovelace(105_000_000)
        |> assets.add(test_listing_policy, test_token_name(), 1),
      datum: InlineDatum(promo_datum),
      reference_script: None,
    }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [consumed_user_input()],
      reference_inputs: [promo_ref_input],
      outputs: [promo_output],
      mint: assets.from_asset(test_listing_policy, test_token_name(), 1),
    }
  listing_nft.mint(test_settings_nft_policy, Mint, test_listing_policy, tx)
}
