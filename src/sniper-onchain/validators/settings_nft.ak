/// Settings NFT Minting Policy (One-Shot)
///
/// Parameterized by a UTxO reference. Ensures exactly one token is minted
/// by consuming the specified UTxO. This creates a unique, non-fungible
/// identifier for the protocol settings.

use aiken/collection/dict
use aiken/collection/list
use cardano/address.{Address, VerificationKey}
use cardano/assets.{PolicyId}
use cardano/transaction.{Input, NoDatum, Output, OutputReference, Transaction}

validator settings_nft(utxo_ref: OutputReference) {
  mint(redeemer: Data, policy_id: PolicyId, tx: Transaction) {
    expect rdm: Int = redeemer
    when rdm is {
      // Mint: one-shot — consume the parameterized UTxO, mint exactly 1 token
      0 -> {
        let utxo_consumed =
          list.any(tx.inputs, fn(input) { input.output_reference == utxo_ref })

        let minted = assets.tokens(tx.mint, policy_id)
        let token_count = dict.size(minted)
        let all_quantity_one =
          dict.foldl(minted, True, fn(_name, qty, acc) { acc && qty == 1 })

        utxo_consumed && token_count == 1 && all_quantity_one
      }
      // Burn: always allowed (all quantities negative)
      1 -> {
        let minted = assets.tokens(tx.mint, policy_id)
        dict.foldl(minted, True, fn(_name, qty, acc) { acc && qty < 0 })
      }
      _ -> False
    }
  }

  else(_) {
    fail
  }
}

// ============================================================================
// Tests
// ============================================================================

const test_tx_hash =
  #"0000000000000000000000000000000000000000000000000000000000000001"

const test_policy_id =
  #"00000000000000000000000000000000000000000000000000000011"

const test_pkh =
  #"00000000000000000000000000000000000000000000000000000001"

fn test_utxo_ref() -> OutputReference {
  OutputReference { transaction_id: test_tx_hash, output_index: 0 }
}

fn test_user_address() -> Address {
  Address {
    payment_credential: VerificationKey(test_pkh),
    stake_credential: None,
  }
}

fn base_input() -> Input {
  Input {
    output_reference: test_utxo_ref(),
    output: Output {
      address: test_user_address(),
      value: assets.from_lovelace(5_000_000),
      datum: NoDatum,
      reference_script: None,
    },
  }
}

test mint_success() {
  let utxo_ref = test_utxo_ref()
  let token_name = "settings"
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [base_input()],
      mint: assets.from_asset(test_policy_id, token_name, 1),
    }
  settings_nft.mint(utxo_ref, 0, test_policy_id, tx)
}

test mint_fails_without_consuming_utxo() fail {
  let utxo_ref = test_utxo_ref()
  let token_name = "settings"
  let other_ref =
    OutputReference { transaction_id: test_tx_hash, output_index: 99 }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [
        Input {
          output_reference: other_ref,
          output: Output {
            address: test_user_address(),
            value: assets.from_lovelace(5_000_000),
            datum: NoDatum,
            reference_script: None,
          },
        },
      ],
      mint: assets.from_asset(test_policy_id, token_name, 1),
    }
  settings_nft.mint(utxo_ref, 0, test_policy_id, tx)
}

test mint_fails_with_quantity_2() fail {
  let utxo_ref = test_utxo_ref()
  let token_name = "settings"
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [base_input()],
      mint: assets.from_asset(test_policy_id, token_name, 2),
    }
  settings_nft.mint(utxo_ref, 0, test_policy_id, tx)
}

test mint_fails_with_two_token_names() fail {
  let utxo_ref = test_utxo_ref()
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [base_input()],
      mint: assets.from_asset(test_policy_id, "token_a", 1)
        |> assets.merge(assets.from_asset(test_policy_id, "token_b", 1)),
    }
  settings_nft.mint(utxo_ref, 0, test_policy_id, tx)
}

test burn_success() {
  let utxo_ref = test_utxo_ref()
  let token_name = "settings"
  let tx =
    Transaction {
      ..transaction.placeholder,
      mint: assets.from_asset(test_policy_id, token_name, -1),
    }
  settings_nft.mint(utxo_ref, 1, test_policy_id, tx)
}

test burn_fails_with_positive_quantity() fail {
  let utxo_ref = test_utxo_ref()
  let token_name = "settings"
  let tx =
    Transaction {
      ..transaction.placeholder,
      mint: assets.from_asset(test_policy_id, token_name, 1),
    }
  settings_nft.mint(utxo_ref, 1, test_policy_id, tx)
}

test invalid_redeemer_fails() fail {
  let utxo_ref = test_utxo_ref()
  let token_name = "settings"
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [base_input()],
      mint: assets.from_asset(test_policy_id, token_name, 1),
    }
  settings_nft.mint(utxo_ref, 42, test_policy_id, tx)
}
