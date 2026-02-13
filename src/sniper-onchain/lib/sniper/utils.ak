use aiken/collection/list
use aiken/crypto.{blake2b_256}
use aiken/math
use aiken/primitive/bytearray
use cardano/address.{Address}
use cardano/assets.{PolicyId, lovelace_of}
use cardano/transaction.{InlineDatum, Output, OutputReference, Transaction}
use sniper/types.{OutputTag, VerificationKeyHash}

// ============================================================================
// Double Satisfaction Prevention
// ============================================================================

/// Compute the tag for an output: blake2b_256(tx_hash ++ output_index_bytes)
pub fn compute_output_tag(oref: OutputReference) -> ByteArray {
  let index_bytes = bytearray.from_int_big_endian(oref.output_index, 0)
  blake2b_256(bytearray.concat(oref.transaction_id, index_bytes))
}

// ============================================================================
// Output Searching
// ============================================================================

/// Find an output paying to a specific address with minimum lovelace and matching inline tag.
pub fn find_tagged_output_to(
  outputs: List<Output>,
  addr: Address,
  min_lovelace: Int,
  expected_tag: ByteArray,
) -> Bool {
  list.any(
    outputs,
    fn(output) {
      output.address == addr && lovelace_of(output.value) >= min_lovelace && has_inline_tag(
        output,
        expected_tag,
      )
    },
  )
}

/// Find an output paying to a specific address that contains at least one token
/// under the given policy ID, with a matching inline tag.
pub fn find_tagged_nft_output_to(
  outputs: List<Output>,
  addr: Address,
  target_policy: PolicyId,
  expected_tag: ByteArray,
) -> Bool {
  list.any(
    outputs,
    fn(output) {
      output.address == addr && assets.has_any_nft(output.value, target_policy) && has_inline_tag(
        output,
        expected_tag,
      )
    },
  )
}

/// Check if an output has an inline datum matching the expected tag.
fn has_inline_tag(output: Output, expected_tag: ByteArray) -> Bool {
  when output.datum is {
    InlineDatum(datum) -> {
      expect tag: OutputTag = datum
      tag.tag == expected_tag
    }
    _ -> False
  }
}

// ============================================================================
// Fee Calculation
// ============================================================================

/// Calculate fee: max(amount * pct / 10000, min_fee) using floor division.
pub fn calculate_fee(amount: Int, pct: Int, min_fee: Int) -> Int {
  math.max(amount * pct / 10000, min_fee)
}

/// Calculate total locked amount from pre-computed absolute fees.
pub fn calculate_locked_amount(
  max_price: Int,
  operator_fee: Int,
  protocol_fee: Int,
  tx_fee_budget: Int,
) -> Int {
  max_price + operator_fee + protocol_fee + tx_fee_budget
}

// ============================================================================
// Signature Checking
// ============================================================================

/// Check if a verification key hash is in the tx extra_signatories.
pub fn signed_by(tx: Transaction, pkh: VerificationKeyHash) -> Bool {
  list.has(tx.extra_signatories, pkh)
}
