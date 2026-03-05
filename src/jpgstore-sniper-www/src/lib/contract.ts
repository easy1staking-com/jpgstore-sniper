import { ContractInfo } from "./types";

/** Block explorer base URL by network ID */
export function explorerBase(networkId: number): string {
  return networkId === 1
    ? "https://cexplorer.io"
    : "https://preview.cexplorer.io";
}

export function txExplorerUrl(
  txHash: string,
  networkId: number
): string {
  return `${explorerBase(networkId)}/tx/${txHash}`;
}

/**
 * Helper to check whether contract info is fully configured
 * (i.e. backend returned valid scripts).
 */
export function isContractReady(c: ContractInfo | null): boolean {
  return !!(
    c &&
    c.listingNftPolicyCbor &&
    c.listingNftPolicyId &&
    c.escrowScriptAddress &&
    c.settingsUtxoTxHash
  );
}
