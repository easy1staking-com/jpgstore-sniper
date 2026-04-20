use aiken/crypto.{VerificationKeyHash}
use aiken_merkle_tree/mt.{Proof}
use cardano/address.{Address, Credential}
use cardano/assets.{AssetName, PolicyId}

/// The name of the token that authenticates the settings UTXO
pub const settings_nft_name: AssetName = "settings"

pub const percentage_denominator: Int = 100

// ============================================================================
// Protocol Settings Datum (lives at settings validator address)
// ============================================================================

pub type SettingsDatum {
  /// Operator Fee, as a percentage
  operator_fee_pct: Int,
  /// Protocol Fee, as a percentage
  protocol_fee_pct: Int,
  /// Min Operator Fee in lovelace
  min_operator_fee: Int,
  /// Min Protocol Fee in lovelace
  min_protocol_fee: Int,
  /// Address to send Protocol Fee too
  protocol_treasury: Address,
  /// Stake Credential where to lock funsd
  stake_credential: Credential,
  /// Budged to be locked to pay for tx fees
  tx_fee_budget: Int,
  /// The PKH of the administrator which can update these settings
  admin_pkh: VerificationKeyHash,
}

pub fn is_valid_settings_datum(datum: SettingsDatum) -> Bool {
  and {
    datum.operator_fee_pct > 0,
    datum.operator_fee_pct < 100,
    datum.protocol_fee_pct >= 0,
    datum.protocol_fee_pct < 100,
    datum.min_operator_fee > 0,
    datum.min_protocol_fee >= 0,
    datum.tx_fee_budget > 0,
  }
}

pub type MerkleMintRedeemer {
  MerkleMint { nfts: List<ByteArray> }
  MerkleBurn
}

// ============================================================================
// Snipe Datum (lives at snipe escrow address, inline)
// ============================================================================

pub type PolicySnipeDatum {
  owner_pkh: VerificationKeyHash,
  nft_destination: Address,
  policy_id: PolicyId,
  max_price: Int,
  protocol_fee: Int,
}

pub type MerkleSnipeDatum {
  owner_pkh: VerificationKeyHash,
  nft_destination: Address,
  merkle_root: ByteArray,
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

pub type MerkleSpendRedeemer {
  MerkleExecute {
    nft_policy_id: PolicyId,
    nft_asset_name: AssetName,
    proof: Proof<ByteArray>,
  }
  MerkleCancel
}
