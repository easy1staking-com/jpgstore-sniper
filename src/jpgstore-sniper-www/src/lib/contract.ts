/**
 * On-chain contract constants — derived from deployed Aiken validators.
 *
 * These are fixed after deployment. Update when redeploying contracts.
 * The settings NFT policy ID is the hash of the settings.settings.mint validator
 * applied with the bootstrap UTxO reference.
 *
 * The listing NFT policy ID is the hash of snipe.policy_snipe.mint applied
 * with the settings NFT policy ID as parameter.
 */

// --- Mainnet deployment ---

/** Settings NFT mint policy compiled code (settings.settings.mint with bootstrap UTxO applied) */
export const SETTINGS_NFT_POLICY_SCRIPT_CBOR =
  process.env.NEXT_PUBLIC_SETTINGS_NFT_POLICY_CBOR ?? "";

/** Policy ID of the settings NFT (hash of the applied settings mint script) */
export const SETTINGS_NFT_POLICY_ID =
  process.env.NEXT_PUBLIC_SETTINGS_NFT_POLICY_ID ?? "";

/** Listing NFT mint policy compiled code (snipe.policy_snipe.mint with settings_nft_policy_id applied) */
export const LISTING_NFT_POLICY_SCRIPT_CBOR =
  process.env.NEXT_PUBLIC_LISTING_NFT_POLICY_CBOR ?? "";

/** Policy ID of the listing NFT (hash of the applied listing mint script) */
export const LISTING_NFT_POLICY_ID =
  process.env.NEXT_PUBLIC_LISTING_NFT_POLICY_ID ?? "";

/** Escrow script address where snipe UTxOs are locked */
export const ESCROW_SCRIPT_ADDRESS =
  process.env.NEXT_PUBLIC_ESCROW_SCRIPT_ADDRESS ?? "";

/** Settings UTxO reference (txHash#index) — the on-chain UTxO holding the settings datum */
export const SETTINGS_UTXO_TX_HASH =
  process.env.NEXT_PUBLIC_SETTINGS_UTXO_TX_HASH ?? "";
export const SETTINGS_UTXO_OUTPUT_INDEX = parseInt(
  process.env.NEXT_PUBLIC_SETTINGS_UTXO_OUTPUT_INDEX ?? "0"
);

/** Network: 0 = testnet, 1 = mainnet */
export const NETWORK_ID = parseInt(
  process.env.NEXT_PUBLIC_NETWORK_ID ?? "1"
);

/** Block explorer base URL */
export const EXPLORER_BASE =
  NETWORK_ID === 1
    ? "https://cexplorer.io"
    : "https://preview.cexplorer.io";

export function txExplorerUrl(txHash: string): string {
  return `${EXPLORER_BASE}/tx/${txHash}`;
}
