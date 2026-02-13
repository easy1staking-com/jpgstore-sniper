use aiken/crypto.{Blake2b_224, Hash, VerificationKey}
use cardano/address.{Address, Credential}
use cardano/assets.{PolicyId}

// ============================================================================
// Common Type Aliases
// ============================================================================

pub type VerificationKeyHash =
  Hash<Blake2b_224, VerificationKey>

// ============================================================================
// Protocol Settings Datum (lives at settings validator address)
// ============================================================================

pub type SettingsDatum {
  operator_fee_pct: Int,
  protocol_fee_pct: Int,
  min_operator_fee: Int,
  min_protocol_fee: Int,
  protocol_treasury: Address,
  stake_credential: Credential,
  tx_fee_budget: Int,
  admin_pkh: VerificationKeyHash,
}

// ============================================================================
// Snipe Datum (lives at snipe escrow address, inline)
// ============================================================================

pub type SnipeDatum {
  owner_pkh: VerificationKeyHash,
  nft_destination: Address,
  policy_id: PolicyId,
  max_price: Int,
  operator_fee: Int,
  protocol_fee: Int,
  protocol_treasury: Address,
  tx_fee_budget: Int,
}

// ============================================================================
// Redeemers
// ============================================================================

pub type SpendRedeemer {
  Execute
  Cancel
}

pub type MintRedeemer {
  Mint
  Burn
}

pub type SettingsRedeemer {
  UpdateSettings
}

// ============================================================================
// Output Tag (inline datum for double satisfaction prevention)
// ============================================================================

pub type OutputTag {
  tag: ByteArray,
}
