use aiken/collection/dict
use aiken/collection/list
use cardano/address.{Address, Script, VerificationKey}
use cardano/assets.{PolicyId}
use cardano/transaction.{
  InlineDatum, Input, NoDatum, Output, OutputReference, Transaction,
}
use sniper/types.{SettingsDatum, settings_nft_name}

/// Settings Spending Validator (Continuous State)
///
/// Parameterized by settings_nft_policy_id. The settings UTxO holds a
/// SettingsDatum and is identified by containing the settings NFT.
///
/// On update: admin signs, settings NFT must be in the spent UTxO,
/// and must be re-output at the same script address with the NFT.
validator settings(utxo_ref: OutputReference) {
  mint(_redeemer: Data, policy_id: PolicyId, tx: Transaction) {
    let utxo_consumed =
      list.any(tx.inputs, fn(input) { input.output_reference == utxo_ref })

    let nft_value = assets.from_asset(policy_id, settings_nft_name, 1)

    let only_mints_nft = tx.mint == nft_value

    expect Some(nft_output) =
      list.find(
        tx.outputs,
        fn(output) { assets.without_lovelace(output.value) == nft_value },
      )

    let nft_sent_to_output =
      when nft_output.address.payment_credential is {
        VerificationKey(hash) -> hash == policy_id
        _ -> False
      }

    expect InlineDatum(datum) = nft_output.datum
    // Should I check the admin_key is in the list of signatories?
    expect _settings_datum: SettingsDatum = datum

    and {
      utxo_consumed,
      only_mints_nft,
      nft_sent_to_output,
    }
  }

  spend(
    datum: Option<SettingsDatum>,
    _redeemer: Data,
    own_ref: OutputReference,
    self: Transaction,
  ) {
    expect Some(input_datum) = datum
    expect settings_datum: SettingsDatum = input_datum

    expect Some(own_input) = transaction.find_input(self.inputs, own_ref)

    let own_address = own_input.output.address
    expect Script(script_hash) = own_address.payment_credential

    // 1. signed by admin
    let signed_by_admin =
      list.has(self.extra_signatories, settings_datum.admin_pkh)
    // 2. output: expected address and expected value
    expect Some(output) =
      list.find(
        self.outputs,
        fn(output) {
          and {
            output.address == own_address,
            assets.has_nft_strict(output.value, script_hash, settings_nft_name),
          }
        },
      )
    expect InlineDatum(output_datum) = output.datum
    expect output_settings_datum: SettingsDatum = output_datum
    let valid_output_settings = and {
        output_settings_datum.operator_fee_pct > 0,
        output_settings_datum.protocol_fee_pct > 0,
        output_settings_datum.min_operator_fee > 0,
        output_settings_datum.min_protocol_fee > 0,
        output_settings_datum.tx_fee_budget > 0,
      }
    expect
      settings_datum.admin_pkh == output_settings_datum.admin_pkh || list.has(
        self.extra_signatories,
        output_settings_datum.admin_pkh,
      )
    // 3. only allowed fields are modified
    and {
      signed_by_admin,
      valid_output_settings,
    }
  }

  else(_) {
    fail
  }
}

// ============================================================================
// Mint Tests
// ============================================================================

const test_tx_hash =
  #"0000000000000000000000000000000000000000000000000000000000000001"

const test_policy_id =
  #"00000000000000000000000000000000000000000000000000000011"

const another_test_policy_id =
  #"00000000000000000000000000000000000000000000000000000012"

const test_pkh = #"00000000000000000000000000000000000000000000000000000001"

const test_admin_pkh =
  #"00000000000000000000000000000000000000000000000000000001"

const test_script_hash =
  #"00000000000000000000000000000000000000000000000000000099"

const test_nft_policy =
  #"00000000000000000000000000000000000000000000000000000022"

const test_treasury_pkh =
  #"00000000000000000000000000000000000000000000000000000033"

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

fn test_settings_datum() -> SettingsDatum {
  SettingsDatum {
    operator_fee_pct: 200,
    protocol_fee_pct: 100,
    min_operator_fee: 1_000_000,
    min_protocol_fee: 1_000_000,
    protocol_treasury: Address {
      payment_credential: VerificationKey(test_treasury_pkh),
      stake_credential: None,
    },
    stake_credential: VerificationKey(test_treasury_pkh),
    tx_fee_budget: 3_000_000,
    admin_pkh: test_admin_pkh,
  }
}

fn mint_output() -> Output {
  let token_name = "settings"
  Output {
    address: address.from_verification_key(test_policy_id),
    value: assets.from_asset(test_policy_id, token_name, 1),
    datum: InlineDatum(test_settings_datum()),
    reference_script: None,
  }
}

test mint_success() {
  let utxo_ref = test_utxo_ref()
  let token_name = "settings"
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [base_input()],
      outputs: [mint_output()],
      mint: assets.from_asset(test_policy_id, token_name, 1),
    }
  settings.mint(utxo_ref, 0, test_policy_id, tx)
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
      outputs: [mint_output()],
      mint: assets.from_asset(test_policy_id, token_name, 1),
    }
  settings.mint(utxo_ref, 0, test_policy_id, tx)
}

test mint_fails_with_quantity_2() fail {
  let utxo_ref = test_utxo_ref()
  let token_name = "settings"
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [base_input()],
      outputs: [mint_output()],
      mint: assets.from_asset(test_policy_id, token_name, 2),
    }
  settings.mint(utxo_ref, 0, test_policy_id, tx)
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
  settings.mint(utxo_ref, 0, test_policy_id, tx)
}

test mint_fails_with_nft_not_set_to_utxo_1() fail {
  let utxo_ref = test_utxo_ref()
  let token_name = "settings"
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [base_input()],
      outputs: [
        Output {
          address: address.from_verification_key(another_test_policy_id),
          value: assets.from_asset(test_policy_id, token_name, 1),
          datum: NoDatum,
          reference_script: None,
        },
      ],
      mint: assets.from_asset(test_policy_id, token_name, 1),
    }
  settings.mint(utxo_ref, 0, test_policy_id, tx)
}

test mint_fails_with_nft_not_set_to_utxo_2() fail {
  let utxo_ref = test_utxo_ref()
  let token_name = "settings"
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [base_input()],
      outputs: [
        Output {
          address: address.from_verification_key(test_policy_id),
          value: assets.zero,
          datum: NoDatum,
          reference_script: None,
        },
      ],
      mint: assets.from_asset(test_policy_id, token_name, 1),
    }
  settings.mint(utxo_ref, 0, test_policy_id, tx)
}

test mint_fails_with_missing_datum() fail {
  let utxo_ref = test_utxo_ref()
  let token_name = "settings"
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [base_input()],
      outputs: [Output { ..mint_output(), datum: NoDatum }],
      mint: assets.from_asset(test_policy_id, token_name, 1),
    }
  settings.mint(utxo_ref, 0, test_policy_id, tx)
}

test mint_fails_with_malformed_datum() fail {
  let utxo_ref = test_utxo_ref()
  let token_name = "settings"
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [base_input()],
      outputs: [Output { ..mint_output(), datum: InlineDatum(#"abba") }],
      mint: assets.from_asset(test_policy_id, token_name, 1),
    }
  settings.mint(utxo_ref, 0, test_policy_id, tx)
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
  settings.mint(utxo_ref, 42, test_policy_id, tx)
}

// ============================================================================
// Spend Tests
// ============================================================================

fn test_script_address() -> Address {
  Address {
    payment_credential: Script(test_script_hash),
    stake_credential: None,
  }
}

fn settings_input_with_nft(oref: OutputReference) -> Input {
  Input {
    output_reference: oref,
    output: Output {
      address: test_script_address(),
      value: assets.from_lovelace(2_000_000)
        |> assets.add(test_nft_policy, "settings", 1),
      datum: InlineDatum(test_settings_datum()),
      reference_script: None,
    },
  }
}

fn settings_continuing_output() -> Output {
  Output {
    address: test_script_address(),
    value: assets.from_lovelace(2_000_000)
      |> assets.add(test_nft_policy, "settings", 1),
    datum: InlineDatum(test_settings_datum()),
    reference_script: None,
  }
}

test update_settings_success() {
  let utxo_ref = test_utxo_ref()
  let own_ref =
    OutputReference {
      transaction_id: #"0000000000000000000000000000000000000000000000000000000000000001",
      output_index: 0,
    }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [settings_input_with_nft(own_ref)],
      outputs: [settings_continuing_output()],
      extra_signatories: [test_admin_pkh],
    }
  settings.spend(
    utxo_ref,
    Some(test_settings_datum()),
    UpdateSettings,
    own_ref,
    tx,
  )
}

test update_settings_fails_without_admin_sig() fail {
  let utxo_ref = test_utxo_ref()
  let own_ref =
    OutputReference {
      transaction_id: #"0000000000000000000000000000000000000000000000000000000000000001",
      output_index: 0,
    }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [settings_input_with_nft(own_ref)],
      outputs: [settings_continuing_output()],
      extra_signatories: [],
    }
  settings.spend(
    utxo_ref,
    Some(test_settings_datum()),
    UpdateSettings,
    own_ref,
    tx,
  )
}

test update_settings_fails_without_nft_in_input() fail {
  let utxo_ref = test_utxo_ref()
  let own_ref =
    OutputReference {
      transaction_id: #"0000000000000000000000000000000000000000000000000000000000000001",
      output_index: 0,
    }
  // Input without the settings NFT
  let input_without_nft =
    Input {
      output_reference: own_ref,
      output: Output {
        address: test_script_address(),
        value: assets.from_lovelace(2_000_000),
        datum: InlineDatum(test_settings_datum()),
        reference_script: None,
      },
    }
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [input_without_nft],
      outputs: [settings_continuing_output()],
      extra_signatories: [test_admin_pkh],
    }
  settings.spend(
    utxo_ref,
    Some(test_settings_datum()),
    UpdateSettings,
    own_ref,
    tx,
  )
}

test update_settings_fails_without_continuing_output() fail {
  let utxo_ref = test_utxo_ref()
  let own_ref =
    OutputReference {
      transaction_id: #"0000000000000000000000000000000000000000000000000000000000000001",
      output_index: 0,
    }
  // No output at the script address with the NFT
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [settings_input_with_nft(own_ref)],
      outputs: [
        Output {
          address: Address {
            payment_credential: VerificationKey(test_admin_pkh),
            stake_credential: None,
          },
          value: assets.from_lovelace(2_000_000),
          datum: NoDatum,
          reference_script: None,
        },
      ],
      extra_signatories: [test_admin_pkh],
    }
  settings.spend(
    utxo_ref,
    Some(test_settings_datum()),
    UpdateSettings,
    own_ref,
    tx,
  )
}

test update_settings_fails_without_datum() fail {
  let utxo_ref = test_utxo_ref()
  let own_ref =
    OutputReference {
      transaction_id: #"0000000000000000000000000000000000000000000000000000000000000001",
      output_index: 0,
    }
  settings.spend(
    utxo_ref,
    None,
    UpdateSettings,
    own_ref,
    transaction.placeholder,
  )
}
