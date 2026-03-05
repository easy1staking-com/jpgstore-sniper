export interface Settings {
  operatorFeePct: number; // basis: pct/100 (e.g. 2 = 2%)
  protocolFeePct: number;
  minOperatorFee: number; // lovelace
  minProtocolFee: number; // lovelace
  txFeeBudget: number; // lovelace
  protocolTreasury: string; // address
}

export interface SnipeOrder {
  id: string;
  txHash?: string; // on-chain UTxO tx hash
  outputIndex?: number; // on-chain UTxO output index
  policyId: string;
  collectionName: string;
  maxPrice: number; // lovelace
  operatorFee: number; // lovelace
  protocolFee: number; // lovelace
  txFeeBudget: number; // lovelace
  totalLocked: number; // lovelace
  status: "active" | "executed" | "cancelled";
  createdAt: string;
}

export interface FeeBreakdown {
  operatorFee: number; // lovelace
  protocolFee: number; // lovelace
  txFeeBudget: number; // lovelace
  totalLocked: number; // lovelace (maxPrice + all fees)
}

export interface CollectionInfo {
  policyId: string;
  name: string;
  description: string;
  imageUrl?: string;
}
