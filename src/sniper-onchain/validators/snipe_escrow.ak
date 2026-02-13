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

use aiken/collection/dict
use aiken/collection/list
use cardano/address.{Address, Script, VerificationKey}
use cardano/assets.{PolicyId}
use cardano/transaction.{
  InlineDatum, Input, Output, OutputReference, Transaction,
}
use sniper/types.{Cancel, Execute, OutputTag, SnipeDatum, SpendRedeemer}
use sniper/utils.{
  compute_output_tag, find_tagged_nft_output_to, find_tagged_output_to,
  signed_by,
}

validator snipe_escrow(listing_nft_policy_id: PolicyId) {
  spend(
    datum: Option<SnipeDatum>,
    redeemer: SpendRedeemer,
    own_ref: OutputReference,
    tx: Transaction,
  ) {
    expect Some(snipe_datum) = datum

    // Find own input
    expect Some(own_input) =
      list.find(
        tx.inputs,
        fn(input) { input.output_reference == own_ref },
      )

    // Listing NFT must be in the consumed UTxO
    let listing_nft_in_input =
      assets.has_any_nft(own_input.output.value, listing_nft_policy_id)

    // Get the listing NFT token name
    let listing_tokens =
      assets.tokens(own_input.output.value, listing_nft_policy_id)
    expect Some(listing_token_name) = list.head(dict.keys(listing_tokens))

    // Listing NFT must be burned (quantity -1 in mint field) to prevent reuse
    let listing_nft_burned =
      assets.quantity_of(tx.mint, listing_nft_policy_id, listing_token_name) == -1

    when redeemer is {
      Execute -> {
        // Compute tag for double satisfaction prevention
        let tag = compute_output_tag(own_ref)

        // Output to nft_destination with at least 1 NFT under target policy_id + tag
        let nft_delivered =
          find_tagged_nft_output_to(
            tx.outputs,
            snipe_datum.nft_destination,
            snipe_datum.policy_id,
            tag,
          )

        // Protocol fee: skip check if fee is 0 (promotion), otherwise verify output
        let protocol_paid =
          if snipe_datum.protocol_fee == 0 {
            True
          } else {
            find_tagged_output_to(
              tx.outputs,
              snipe_datum.protocol_treasury,
              snipe_datum.protocol_fee,
              tag,
            )
          }

        listing_nft_in_input && listing_nft_burned && nft_delivered && protocol_paid
      }

      Cancel ->
        listing_nft_in_input && listing_nft_burned && signed_by(
          tx,
          snipe_datum.owner_pkh,
        )
    }
  }

  else(_) {
    fail
  }
}

// ============================================================================
// Tests
// ============================================================================

const test_listing_policy =
  #"00000000000000000000000000000000000000000000000000000044"

const test_target_policy =
  #"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

const test_owner_pkh =
  #"00000000000000000000000000000000000000000000000000000001"

const test_treasury_pkh =
  #"00000000000000000000000000000000000000000000000000000033"

const test_escrow_script_hash =
  #"00000000000000000000000000000000000000000000000000000055"

const test_listing_token_name = "listing_token"

fn test_snipe_oref() -> OutputReference {
  OutputReference {
    transaction_id: #"0000000000000000000000000000000000000000000000000000000000000001",
    output_index: 0,
  }
}

fn test_treasury_address() -> Address {
  Address {
    payment_credential: VerificationKey(test_treasury_pkh),
    stake_credential: None,
  }
}

fn test_owner_address() -> Address {
  Address {
    payment_credential: VerificationKey(test_owner_pkh),
    stake_credential: None,
  }
}

fn test_snipe_datum() -> SnipeDatum {
  SnipeDatum {
    owner_pkh: test_owner_pkh,
    nft_destination: test_owner_address(),
    policy_id: test_target_policy,
    max_price: 100_000_000,
    operator_fee: 2_000_000,
    protocol_fee: 1_000_000,
    protocol_treasury: test_treasury_address(),
    tx_fee_budget: 3_000_000,
  }
}

fn escrow_input(oref: OutputReference) -> Input {
  Input {
    output_reference: oref,
    output: Output {
      address: Address {
        payment_credential: Script(test_escrow_script_hash),
        stake_credential: None,
      },
      value: assets.from_lovelace(106_000_000)
        |> assets.add(test_listing_policy, test_listing_token_name, 1),
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
    datum: InlineDatum(OutputTag { tag }),
    reference_script: None,
  }
}

fn protocol_fee_output(tag: ByteArray) -> Output {
  Output {
    address: test_treasury_address(),
    value: assets.from_lovelace(1_000_000),
    datum: InlineDatum(OutputTag { tag }),
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
      mint: assets.from_asset(test_listing_policy, test_listing_token_name, -1),
    }
  snipe_escrow.spend(
    test_listing_policy,
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
      mint: assets.from_asset(test_listing_policy, test_listing_token_name, -1),
    }
  snipe_escrow.spend(
    test_listing_policy,
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
      mint: assets.from_asset(test_listing_policy, test_listing_token_name, -1),
    }
  snipe_escrow.spend(
    test_listing_policy,
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
    }
  snipe_escrow.spend(
    test_listing_policy,
    Some(test_snipe_datum()),
    Execute,
    oref,
    tx,
  )
}

test execute_fails_without_listing_nft_in_input() fail {
  let oref = test_snipe_oref()
  let tag = compute_output_tag(oref)
  let input_no_nft =
    Input {
      output_reference: oref,
      output: Output {
        address: Address {
          payment_credential: Script(test_escrow_script_hash),
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
      mint: assets.from_asset(test_listing_policy, test_listing_token_name, -1),
    }
  snipe_escrow.spend(
    test_listing_policy,
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
      mint: assets.from_asset(test_listing_policy, test_listing_token_name, -1),
    }
  snipe_escrow.spend(
    test_listing_policy,
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
      datum: InlineDatum(OutputTag { tag }),
      reference_script: None,
    }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [escrow_input(oref)],
      outputs: [nft_delivery_output(tag), low_fee_output],
      mint: assets.from_asset(test_listing_policy, test_listing_token_name, -1),
    }
  snipe_escrow.spend(
    test_listing_policy,
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
  let promo_datum =
    SnipeDatum { ..test_snipe_datum(), protocol_fee: 0 }
  let promo_input =
    Input {
      output_reference: oref,
      output: Output {
        address: Address {
          payment_credential: Script(test_escrow_script_hash),
          stake_credential: None,
        },
        value: assets.from_lovelace(105_000_000)
          |> assets.add(test_listing_policy, test_listing_token_name, 1),
        datum: InlineDatum(promo_datum),
        reference_script: None,
      },
    }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [promo_input],
      outputs: [nft_delivery_output(tag)],
      mint: assets.from_asset(test_listing_policy, test_listing_token_name, -1),
    }
  snipe_escrow.spend(
    test_listing_policy,
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
      mint: assets.from_asset(test_listing_policy, test_listing_token_name, -1),
    }
  snipe_escrow.spend(
    test_listing_policy,
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
      mint: assets.from_asset(test_listing_policy, test_listing_token_name, -1),
    }
  snipe_escrow.spend(
    test_listing_policy,
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
  snipe_escrow.spend(
    test_listing_policy,
    Some(test_snipe_datum()),
    Cancel,
    oref,
    tx,
  )
}

test cancel_fails_wrong_signer() fail {
  let oref = test_snipe_oref()
  let wrong_pkh =
    #"ffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [escrow_input(oref)],
      extra_signatories: [wrong_pkh],
      mint: assets.from_asset(test_listing_policy, test_listing_token_name, -1),
    }
  snipe_escrow.spend(
    test_listing_policy,
    Some(test_snipe_datum()),
    Cancel,
    oref,
    tx,
  )
}
