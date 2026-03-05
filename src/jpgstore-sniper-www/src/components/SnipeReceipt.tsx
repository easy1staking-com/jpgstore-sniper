"use client";

import { CollectionInfo, FeeBreakdown, Settings } from "@/lib/types";
import { formatAda } from "@/lib/fees";

interface SnipeReceiptProps {
  collection: CollectionInfo;
  maxPrice: number;
  quantity: number;
  fees: FeeBreakdown;
  settings: Settings;
  submitting?: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

function truncate(s: string): string {
  return `${s.slice(0, 8)}...${s.slice(-8)}`;
}

function isMinFee(computed: number, min: number): boolean {
  return computed <= min;
}

export default function SnipeReceipt({
  collection,
  maxPrice,
  quantity,
  fees,
  settings,
  submitting,
  onCancel,
  onConfirm,
}: SnipeReceiptProps) {
  const rawOperator = Math.ceil((maxPrice * settings.operatorFeePct) / 100);
  const rawProtocol = Math.ceil((maxPrice * settings.protocolFeePct) / 100);
  const operatorIsMin = isMinFee(rawOperator, settings.minOperatorFee);
  const protocolIsMin = isMinFee(rawProtocol, settings.minProtocolFee);
  const orderTotal = fees.totalLocked * quantity;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm">
      <div className="w-full max-w-md rounded-xl border border-border-bright bg-surface p-6 shadow-[0_0_40px_rgba(0,0,0,0.5)] animate-slide-up">
        {/* Header */}
        <div className="flex items-center gap-3 mb-5">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-neon/10">
            <svg viewBox="0 0 16 16" className="h-4 w-4 text-neon" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
              <rect x="2" y="2" width="12" height="12" rx="2" />
              <polyline points="5,8 7,10 11,6" />
            </svg>
          </div>
          <h2 className="text-base font-semibold text-text">
            Snipe Order Receipt
          </h2>
        </div>

        {/* Summary */}
        <div className="space-y-2.5 text-sm">
          <Row label="Collection" value={collection.name} highlight />
          <Row label="Policy ID" value={truncate(collection.policyId)} mono />
          <Row label="Max Price" value={`${formatAda(maxPrice)} ADA`} mono />
          <Row
            label="Quantity"
            value={`${quantity} snipe${quantity > 1 ? "s" : ""}`}
          />
        </div>

        {/* Per Snipe Breakdown */}
        <div className="mt-5 border-t border-border pt-4">
          <p className="mb-3 text-[11px] font-semibold uppercase tracking-[0.15em] text-text-dim">
            Per Snipe Breakdown
          </p>
          <div className="space-y-2 text-sm">
            <Row label="Max Price" value={`${formatAda(maxPrice)} ADA`} mono />
            <Row
              label={`Operator Fee (${settings.operatorFeePct}%)`}
              value={`${formatAda(fees.operatorFee)} ADA`}
              mono
              tag={operatorIsMin ? "min" : undefined}
            />
            <Row
              label={`Protocol Fee (${settings.protocolFeePct}%)`}
              value={`${formatAda(fees.protocolFee)} ADA`}
              mono
              tag={protocolIsMin ? "min" : undefined}
            />
            <Row
              label="Tx Fee Budget"
              value={`${formatAda(fees.txFeeBudget)} ADA`}
              mono
            />
            <div className="flex items-center justify-between border-t border-border pt-2.5 mt-2">
              <span className="text-xs uppercase tracking-wider text-text-dim font-medium">
                Locked per snipe
              </span>
              <span className="font-mono text-sm font-semibold text-neon">
                {formatAda(fees.totalLocked)} ADA
              </span>
            </div>
          </div>
        </div>

        {/* Order Total */}
        {quantity > 1 && (
          <div className="mt-4 border-t border-border pt-4">
            <p className="mb-3 text-[11px] font-semibold uppercase tracking-[0.15em] text-text-dim">
              Order Total
            </p>
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-text-mid">
                Total Locked
              </span>
              <span className="font-mono text-base font-bold text-neon">
                {formatAda(orderTotal)} ADA
              </span>
            </div>
            <p className="mt-1 text-xs text-text-dim font-mono">
              ({quantity} &times; {formatAda(fees.totalLocked)} ADA)
            </p>
          </div>
        )}

        {/* Actions */}
        <div className="mt-6 flex gap-3">
          <button
            onClick={onCancel}
            disabled={submitting}
            className="flex-1 rounded-lg border border-border-bright px-4 py-2.5 text-sm font-medium text-text-mid transition-all hover:bg-surface-light hover:text-text disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Cancel
          </button>
          <button
            onClick={onConfirm}
            disabled={submitting}
            className="flex-1 rounded-lg bg-neon px-4 py-2.5 text-sm font-semibold text-void transition-all hover:bg-neon-dim shadow-[0_0_16px_var(--color-neon-glow)] hover:shadow-[0_0_24px_var(--color-neon-glow)] disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {submitting ? (
              <span className="flex items-center justify-center gap-2">
                <span className="inline-block h-4 w-4 rounded-full border-2 border-void/30 border-t-void animate-spin" />
                Signing...
              </span>
            ) : (
              "Confirm & Sign"
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

function Row({
  label,
  value,
  mono,
  tag,
  highlight,
}: {
  label: string;
  value: string;
  mono?: boolean;
  tag?: string;
  highlight?: boolean;
}) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-text-dim">{label}</span>
      <span
        className={`${mono ? "font-mono" : ""} ${highlight ? "text-neon font-medium" : "text-text"}`}
      >
        {value}
        {tag && (
          <span className="ml-1.5 rounded border border-amber/30 bg-amber/10 px-1 py-0.5 text-[10px] text-amber font-medium">
            {tag}
          </span>
        )}
      </span>
    </div>
  );
}
