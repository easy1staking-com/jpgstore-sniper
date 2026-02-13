use aiken/collection/list
use cardano/address.{Address, Script, VerificationKey}
use cardano/assets.{PolicyId}
use cardano/transaction.{
  InlineDatum, Input, NoDatum, Output, OutputReference, Transaction,
}
use sniper/types.{SettingsDatum, SettingsRedeemer, UpdateSettings}

/// Settings Spending Validator (Continuous State)
///
/// Parameterized by settings_nft_policy_id. The settings UTxO holds a
/// SettingsDatum and is identified by containing the settings NFT.
///
/// On update: admin signs, settings NFT must be in the spent UTxO,
/// and must be re-output at the same script address with the NFT.
validator settings(settings_nft_policy_id: PolicyId) {
  spend(
    datum: Option<SettingsDatum>,
    redeemer: SettingsRedeemer,
    own_ref: OutputReference,
    tx: Transaction,
  ) {
    expect Some(settings_datum) = datum
    when redeemer is {
      UpdateSettings -> {
        // Admin must sign
        let admin_signed =
          list.has(tx.extra_signatories, settings_datum.admin_pkh)

        // Find own input to get the script hash
        expect Some(own_input) =
          list.find(tx.inputs, fn(input) { input.output_reference == own_ref })
        expect Script(own_script_hash) =
          own_input.output.address.payment_credential

        // Settings NFT must be in the consumed UTxO
        let nft_in_input =
          assets.has_any_nft(own_input.output.value, settings_nft_policy_id)

        // Must produce an output at the same script address with the settings NFT
        let continuing_output =
          list.find(
            tx.outputs,
            fn(output) {
              when output.address.payment_credential is {
                Script(hash) ->
                  hash == own_script_hash && assets.has_any_nft(
                    output.value,
                    settings_nft_policy_id,
                  )
                _ -> False
              }
            },
          )

        // Continuing output must have an inline datum (new settings)
        let has_valid_datum =
          when continuing_output is {
            Some(output) ->
              when output.datum is {
                InlineDatum(data) -> {
                  // Just verify it can be parsed as SettingsDatum
                  expect _new_settings: SettingsDatum = data
                  True
                }
                _ -> False
              }
            None -> False
          }

        admin_signed && nft_in_input && has_valid_datum
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

const test_admin_pkh =
  #"00000000000000000000000000000000000000000000000000000001"

const test_script_hash =
  #"00000000000000000000000000000000000000000000000000000099"

const test_nft_policy =
  #"00000000000000000000000000000000000000000000000000000022"

const test_treasury_pkh =
  #"00000000000000000000000000000000000000000000000000000033"

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
    test_nft_policy,
    Some(test_settings_datum()),
    UpdateSettings,
    own_ref,
    tx,
  )
}

test update_settings_fails_without_admin_sig() fail {
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
    test_nft_policy,
    Some(test_settings_datum()),
    UpdateSettings,
    own_ref,
    tx,
  )
}

test update_settings_fails_without_nft_in_input() fail {
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
    test_nft_policy,
    Some(test_settings_datum()),
    UpdateSettings,
    own_ref,
    tx,
  )
}

test update_settings_fails_without_continuing_output() fail {
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
    test_nft_policy,
    Some(test_settings_datum()),
    UpdateSettings,
    own_ref,
    tx,
  )
}

test update_settings_fails_without_datum() fail {
  let own_ref =
    OutputReference {
      transaction_id: #"0000000000000000000000000000000000000000000000000000000000000001",
      output_index: 0,
    }
  settings.spend(
    test_nft_policy,
    None,
    UpdateSettings,
    own_ref,
    transaction.placeholder,
  )
}
