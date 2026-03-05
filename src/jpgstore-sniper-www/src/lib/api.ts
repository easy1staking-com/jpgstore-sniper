import { CollectionInfo, ContractInfo, Settings, SnipeOrder } from "./types";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";

export async function fetchCollection(
  policyId: string
): Promise<CollectionInfo> {
  const res = await fetch(`${API_BASE}/api/v1/collections/${policyId}`);
  if (!res.ok) {
    throw new Error(`Collection not found (${res.status})`);
  }
  return res.json();
}

export async function fetchSettings(): Promise<Settings> {
  const res = await fetch(`${API_BASE}/api/v1/settings`);
  if (!res.ok) {
    throw new Error(`Failed to fetch settings (${res.status})`);
  }
  return res.json();
}

export async function fetchContracts(): Promise<ContractInfo> {
  const res = await fetch(`${API_BASE}/api/v1/contracts`);
  if (!res.ok) {
    throw new Error(`Failed to fetch contracts (${res.status})`);
  }
  return res.json();
}

interface BackendSnipe {
  txHash: string;
  outputIndex: number;
  ownerPkh: string;
  nftDestination: string;
  targetHash: string;
  maxPrice: number;
  protocolFee: number;
}

export async function fetchSnipes(
  walletPkh?: string
): Promise<SnipeOrder[]> {
  const res = await fetch(`${API_BASE}/api/v1/snipes`);
  if (!res.ok) {
    throw new Error(`Failed to fetch snipes (${res.status})`);
  }
  const snipes: BackendSnipe[] = await res.json();

  return snipes
    .filter((s) => !walletPkh || s.ownerPkh === walletPkh)
    .map((s) => ({
      id: `${s.txHash}#${s.outputIndex}`,
      txHash: s.txHash,
      outputIndex: s.outputIndex,
      policyId: s.targetHash,
      collectionName: s.targetHash.slice(0, 8) + "...",
      maxPrice: s.maxPrice,
      operatorFee: 0, // not stored on-chain individually, computed from settings
      protocolFee: s.protocolFee,
      txFeeBudget: 0,
      totalLocked: 0, // will be the UTxO value on-chain
      status: "active" as const,
      createdAt: "",
    }));
}
