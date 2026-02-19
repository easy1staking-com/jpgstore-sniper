use aiken/crypto.{Blake2b_224, Hash, VerificationKey}
use cardano/address.{Address, Credential}
use cardano/assets.{AssetName, PolicyId}

/// The name of the token that authenticates the settings UTXO
pub const settings_nft_name: AssetName = "settings"

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
  // in lovelace
  min_operator_fee: Int,
  // in lovelace
  min_protocol_fee: Int,
  protocol_treasury: Address,
  stake_credential: Credential,
  // in lovelace
  tx_fee_budget: Int,
  admin_pkh: VerificationKeyHash,
}

pub fn is_valid_settings_datum(datum: SettingsDatum) -> Bool {
  and {
    datum.operator_fee_pct > 0,
    datum.protocol_fee_pct > 0,
    datum.min_operator_fee > 0,
    datum.min_protocol_fee > 0,
    datum.tx_fee_budget > 0,
  }
}

// ============================================================================
// Snipe Datum (lives at snipe escrow address, inline)
// ============================================================================

pub type SnipeDatum {
  owner_pkh: VerificationKeyHash,
  nft_destination: Address,
  policy_id: PolicyId,
  max_price: Int,
  protocol_fee: Int,
}

// ============================================================================
// Redeemers
// ============================================================================

pub type SpendRedeemer {
  Execute
  Cancel
}

// ============================================================================
// Output Tag (inline datum for double satisfaction prevention)
// ============================================================================

pub type OutputTag {
  tag: ByteArray,
}
