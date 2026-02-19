use aiken/cbor.{serialise}
use aiken/collection/list
use aiken/crypto.{blake2b_256}
use cardano/transaction.{OutputReference, Transaction}
use sniper/types.{VerificationKeyHash}

// ============================================================================
// Double Satisfaction Prevention
// ============================================================================

/// Compute the tag for an output: blake2b_256(tx_hash ++ output_index_bytes)
pub fn compute_output_tag(oref: OutputReference) -> ByteArray {
  oref
    |> serialise
    |> blake2b_256
}

// ============================================================================
// Signature Checking
// ============================================================================

/// Check if a verification key hash is in the tx extra_signatories.
pub fn signed_by(tx: Transaction, pkh: VerificationKeyHash) -> Bool {
  list.has(tx.extra_signatories, pkh)
}
