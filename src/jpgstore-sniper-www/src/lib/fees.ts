import { FeeBreakdown, Settings } from "./types";

/**
 * Calculate fees for a snipe order — mirrors on-chain contract math exactly.
 * fee = max(ceil(maxPrice * pct / 100), minFee)
 */
export function calculateFees(
  settings: Settings,
  maxPrice: number
): FeeBreakdown {
  const operatorFee = Math.max(
    Math.ceil((maxPrice * settings.operatorFeePct) / 100),
    settings.minOperatorFee
  );

  const protocolFee = Math.max(
    Math.ceil((maxPrice * settings.protocolFeePct) / 100),
    settings.minProtocolFee
  );

  const totalLocked =
    maxPrice + operatorFee + protocolFee + settings.txFeeBudget;

  return {
    operatorFee,
    protocolFee,
    txFeeBudget: settings.txFeeBudget,
    totalLocked,
  };
}

/** Convert lovelace to ADA (1 ADA = 1,000,000 lovelace) */
export function lovelaceToAda(lovelace: number): number {
  return lovelace / 1_000_000;
}

/** Convert ADA to lovelace */
export function adaToLovelace(ada: number): number {
  return Math.round(ada * 1_000_000);
}

/** Format lovelace as ADA string with 6 decimal places */
export function formatAda(lovelace: number): string {
  return lovelaceToAda(lovelace).toFixed(6);
}
