use aiken/fuzz.{and_then, map}
use cardano/address.{Address, VerificationKey}
use cardano/assets
use cardano/transaction.{
  InlineDatum, Input, Output, OutputReference, Transaction,
}
use settings.{
  base_input, mint_script_address, test_script_address, test_utxo_ref,
}
use sniper/types.{SettingsDatum, is_valid_settings_datum, settings_nft_name}
use sniper/utils.{calculate_fee, calculate_locked_amount}

const test_policy_id =
  #"00000000000000000000000000000000000000000000000000000011"

const test_script_hash =
  #"00000000000000000000000000000000000000000000000000000099"

const test_treasury_pkh =
  #"00000000000000000000000000000000000000000000000000000033"

// ============================================================================
// Fuzzers
// ============================================================================

fn verification_key_hash() -> Fuzzer<ByteArray> {
  fuzz.bytearray_fixed(28)
}

fn address_fuzzer() -> Fuzzer<Address> {
  let pkh <- map(verification_key_hash())
  Address { payment_credential: VerificationKey(pkh), stake_credential: None }
}

fn credential_fuzzer() -> Fuzzer<address.Credential> {
  let hash <- map(verification_key_hash())
  VerificationKey(hash)
}

/// Generate a SettingsDatum with ALL fields > 0 (valid)
pub fn valid_settings_datum() -> Fuzzer<SettingsDatum> {
  let operator_fee_pct <- and_then(fuzz.int_between(1, 5000))
  let protocol_fee_pct <- and_then(fuzz.int_between(1, 5000))
  let min_operator_fee <- and_then(fuzz.int_between(1, 100_000_000))
  let min_protocol_fee <- and_then(fuzz.int_between(1, 100_000_000))
  let protocol_treasury <- and_then(address_fuzzer())
  let stake_credential <- and_then(credential_fuzzer())
  let tx_fee_budget <- and_then(fuzz.int_between(1, 10_000_000))
  let admin_pkh <- map(verification_key_hash())
  SettingsDatum {
    operator_fee_pct,
    protocol_fee_pct,
    min_operator_fee,
    min_protocol_fee,
    protocol_treasury,
    stake_credential,
    tx_fee_budget,
    admin_pkh,
  }
}

/// Generate a SettingsDatum with exactly one field set to 0 or negative
pub fn invalid_settings_datum() -> Fuzzer<SettingsDatum> {
  let base <- and_then(valid_settings_datum())
  let bad_value <-
    and_then(fuzz.either(fuzz.constant(0), fuzz.int_between(-100, -1)))
  let field <- map(fuzz.int_between(0, 4))
  when field is {
    0 -> SettingsDatum { ..base, operator_fee_pct: bad_value }
    1 -> SettingsDatum { ..base, protocol_fee_pct: bad_value }
    2 -> SettingsDatum { ..base, min_operator_fee: bad_value }
    3 -> SettingsDatum { ..base, min_protocol_fee: bad_value }
    _ -> SettingsDatum { ..base, tx_fee_budget: bad_value }
  }
}

// ============================================================================
// is_valid_settings_datum — Property Tests
// ============================================================================

test prop_valid_datum_always_accepted(datum via valid_settings_datum()) {
  is_valid_settings_datum(datum)
}

test prop_invalid_datum_always_rejected(datum via invalid_settings_datum()) {
  !is_valid_settings_datum(datum)
}

// ============================================================================
// Fee Calculation — Property Tests
// ============================================================================

fn fee_params() -> Fuzzer<(Int, Int, Int)> {
  let amount <- and_then(fuzz.int_between(0, 1_000_000_000))
  let pct <- and_then(fuzz.int_between(0, 5000))
  let min_fee <- map(fuzz.int_between(0, 10_000_000))
  (amount, pct, min_fee)
}

test prop_fee_never_below_min(params via fee_params()) {
  let (amount, pct, min_fee) = params
  calculate_fee(amount, pct, min_fee) >= min_fee
}

test prop_fee_never_below_pct(params via fee_params()) {
  let (amount, pct, min_fee) = params
  let pct_fee = amount * pct / 10000
  calculate_fee(amount, pct, min_fee) >= pct_fee
}

test prop_fee_is_max_of_pct_and_min(params via fee_params()) {
  let (amount, pct, min_fee) = params
  let result = calculate_fee(amount, pct, min_fee)
  let pct_fee = amount * pct / 10000
  result == pct_fee || result == min_fee
}

fn locked_amount_params() -> Fuzzer<(Int, Int, Int, Int)> {
  let max_price <- and_then(fuzz.int_between(1_000_000, 1_000_000_000))
  let op_fee <- and_then(fuzz.int_between(0, 10_000_000))
  let proto_fee <- and_then(fuzz.int_between(0, 10_000_000))
  let budget <- map(fuzz.int_between(1_000_000, 10_000_000))
  (max_price, op_fee, proto_fee, budget)
}

test prop_locked_amount_gte_max_price(params via locked_amount_params()) {
  let (max_price, op_fee, proto_fee, budget) = params
  calculate_locked_amount(max_price, op_fee, proto_fee, budget) >= max_price
}

test prop_locked_amount_is_sum(params via locked_amount_params()) {
  let (max_price, op_fee, proto_fee, budget) = params
  calculate_locked_amount(max_price, op_fee, proto_fee, budget) == max_price + op_fee + proto_fee + budget
}

// ============================================================================
// Property-Based Tests — Mint
// ============================================================================

fn valid_mint_tx(datum: SettingsDatum) -> Transaction {
  let output =
    Output {
      address: mint_script_address(),
      value: assets.from_lovelace(2_000_000)
        |> assets.add(test_policy_id, settings_nft_name, 1),
      datum: InlineDatum(datum),
      reference_script: None,
    }
  Transaction {
    ..transaction.placeholder,
    inputs: [base_input()],
    outputs: [output],
    mint: assets.from_asset(test_policy_id, settings_nft_name, 1),
    extra_signatories: [datum.admin_pkh],
  }
}

test prop_mint_accepts_any_valid_datum(datum via valid_settings_datum()) {
  let utxo_ref = test_utxo_ref()
  settings.settings.mint(utxo_ref, 0, test_policy_id, valid_mint_tx(datum))
}

test prop_mint_rejects_any_invalid_datum(
  datum via invalid_settings_datum(),
) fail {
  let utxo_ref = test_utxo_ref()
  settings.settings.mint(utxo_ref, 0, test_policy_id, valid_mint_tx(datum))
}

test prop_mint_rejects_without_admin_sig(datum via valid_settings_datum()) fail {
  let utxo_ref = test_utxo_ref()
  let tx = Transaction { ..valid_mint_tx(datum), extra_signatories: [] }
  settings.settings.mint(utxo_ref, 0, test_policy_id, tx)
}

// ============================================================================
// Property-Based Tests — Spend
// ============================================================================

fn settings_input_with_datum(
  oref: OutputReference,
  datum: SettingsDatum,
) -> Input {
  Input {
    output_reference: oref,
    output: Output {
      address: test_script_address(),
      value: assets.from_lovelace(2_000_000)
        |> assets.add(test_script_hash, settings_nft_name, 1),
      datum: InlineDatum(datum),
      reference_script: None,
    },
  }
}

fn settings_output_with_datum(datum: SettingsDatum) -> Output {
  Output {
    address: test_script_address(),
    value: assets.from_lovelace(2_000_000)
      |> assets.add(test_script_hash, settings_nft_name, 1),
    datum: InlineDatum(datum),
    reference_script: None,
  }
}

fn valid_spend_pair() -> Fuzzer<(SettingsDatum, SettingsDatum)> {
  fuzz.both(valid_settings_datum(), valid_settings_datum())
}

fn invalid_output_spend_pair() -> Fuzzer<(SettingsDatum, SettingsDatum)> {
  fuzz.both(valid_settings_datum(), invalid_settings_datum())
}

test prop_spend_accepts_valid_update(pair via valid_spend_pair()) {
  let (input_datum, raw_output_datum) = pair
  let output_datum =
    SettingsDatum { ..raw_output_datum, admin_pkh: input_datum.admin_pkh }
  let own_ref = test_utxo_ref()
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [settings_input_with_datum(own_ref, input_datum)],
      outputs: [settings_output_with_datum(output_datum)],
      extra_signatories: [input_datum.admin_pkh],
    }
  settings.settings.spend(test_utxo_ref(), Some(input_datum), Void, own_ref, tx)
}

test prop_spend_rejects_invalid_output_datum(
  pair via invalid_output_spend_pair(),
) fail {
  let (input_datum, raw_output_datum) = pair
  let output_datum =
    SettingsDatum { ..raw_output_datum, admin_pkh: input_datum.admin_pkh }
  let own_ref = test_utxo_ref()
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [settings_input_with_datum(own_ref, input_datum)],
      outputs: [settings_output_with_datum(output_datum)],
      extra_signatories: [input_datum.admin_pkh],
    }
  settings.settings.spend(test_utxo_ref(), Some(input_datum), Void, own_ref, tx)
}

// ============================================================================
// Property-Based Tests — Admin Key Rotation
// ============================================================================

fn two_distinct_admins() -> Fuzzer<(ByteArray, ByteArray)> {
  let old_admin <- and_then(fuzz.bytearray_fixed(28))
  let new_admin <-
    map(fuzz.such_that(fuzz.bytearray_fixed(28), fn(h) { h != old_admin }))
  (old_admin, new_admin)
}

fn rotation_base_datum(admin: ByteArray) -> SettingsDatum {
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
    admin_pkh: admin,
  }
}

test prop_rotation_succeeds_with_both_sigs(admins via two_distinct_admins()) {
  let (old_admin, new_admin) = admins
  let input_datum = rotation_base_datum(old_admin)
  let output_datum = SettingsDatum { ..input_datum, admin_pkh: new_admin }
  let own_ref = test_utxo_ref()
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [settings_input_with_datum(own_ref, input_datum)],
      outputs: [settings_output_with_datum(output_datum)],
      extra_signatories: [old_admin, new_admin],
    }
  settings.settings.spend(test_utxo_ref(), Some(input_datum), Void, own_ref, tx)
}

test prop_rotation_fails_without_new_admin_sig(
  admins via two_distinct_admins(),
) fail {
  let (old_admin, new_admin) = admins
  let input_datum = rotation_base_datum(old_admin)
  let output_datum = SettingsDatum { ..input_datum, admin_pkh: new_admin }
  let own_ref = test_utxo_ref()
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [settings_input_with_datum(own_ref, input_datum)],
      outputs: [settings_output_with_datum(output_datum)],
      extra_signatories: [old_admin],
    }
  settings.settings.spend(test_utxo_ref(), Some(input_datum), Void, own_ref, tx)
}

test prop_rotation_fails_without_old_admin_sig(
  admins via two_distinct_admins(),
) fail {
  let (old_admin, new_admin) = admins
  let input_datum = rotation_base_datum(old_admin)
  let output_datum = SettingsDatum { ..input_datum, admin_pkh: new_admin }
  let own_ref = test_utxo_ref()
  let tx =
    Transaction {
      ..transaction.placeholder,
      inputs: [settings_input_with_datum(own_ref, input_datum)],
      outputs: [settings_output_with_datum(output_datum)],
      extra_signatories: [new_admin],
    }
  settings.settings.spend(test_utxo_ref(), Some(input_datum), Void, own_ref, tx)
}
