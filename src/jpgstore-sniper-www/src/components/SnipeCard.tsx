"use client";

import { SnipeOrder } from "@/lib/types";
import { formatAda } from "@/lib/fees";

interface SnipeCardProps {
  order: SnipeOrder;
  selected: boolean;
  onToggle: (id: string) => void;
  index: number;
}

function truncatePolicyId(policyId: string): string {
  return `${policyId.slice(0, 8)}...${policyId.slice(-8)}`;
}

export default function SnipeCard({
  order,
  selected,
  onToggle,
  index,
}: SnipeCardProps) {
  return (
    <div
      className="animate-fade-in"
      style={{ animationDelay: `${index * 60}ms` }}
    >
      <div
        onClick={() => onToggle(order.id)}
        className={`
          group relative cursor-pointer rounded-lg border p-4
          transition-all duration-200
          ${
            selected
              ? "border-neon/40 bg-neon/[0.04] shadow-[0_0_16px_rgba(0,255,136,0.06)]"
              : "border-border bg-surface hover:border-border-bright hover:bg-surface-light/50"
          }
        `}
      >
        {/* left accent bar */}
        <div
          className={`absolute left-0 top-3 bottom-3 w-[3px] rounded-full transition-all duration-200 ${
            selected ? "bg-neon shadow-[0_0_8px_var(--color-neon-glow)]" : "bg-border group-hover:bg-text-dim"
          }`}
        />

        <div className="flex items-start gap-3 pl-3">
          <input
            type="checkbox"
            checked={selected}
            onChange={() => onToggle(order.id)}
            onClick={(e) => e.stopPropagation()}
            className="mt-0.5 shrink-0"
          />
          <div className="flex-1 min-w-0">
            {/* Top row: name + status */}
            <div className="flex items-center justify-between gap-3">
              <h3 className="font-semibold text-text truncate">
                {order.collectionName}
              </h3>
              <span className="shrink-0 flex items-center gap-1.5 text-[11px] font-mono font-medium uppercase tracking-wider text-neon-dim">
                <span className="inline-block h-1.5 w-1.5 rounded-full bg-neon animate-[pulseGlow_2s_ease-in-out_infinite]" />
                Active
              </span>
            </div>

            {/* Policy ID */}
            <p className="mt-1 font-mono text-xs text-text-dim">
              {truncatePolicyId(order.policyId)}
            </p>

            {/* Fee grid */}
            <div className="mt-3 grid grid-cols-2 gap-x-6 gap-y-1.5 text-[13px]">
              <FeeRow label="Max Price" value={`${formatAda(order.maxPrice)}`} />
              <FeeRow label="Operator Fee" value={`${formatAda(order.operatorFee)}`} />
              <FeeRow label="Protocol Fee" value={`${formatAda(order.protocolFee)}`} />
              <FeeRow label="Tx Budget" value={`${formatAda(order.txFeeBudget)}`} />
            </div>

            {/* Total */}
            <div className="mt-3 flex items-center justify-between border-t border-border pt-2.5">
              <span className="text-xs uppercase tracking-wider text-text-dim font-medium">
                Total Locked
              </span>
              <span className="font-mono text-sm font-semibold text-neon">
                {formatAda(order.totalLocked)}{" "}
                <span className="text-neon-dim text-xs">ADA</span>
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function FeeRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-text-dim">{label}</span>
      <span className="font-mono text-text-mid">
        {value} <span className="text-text-dim text-[11px]">ADA</span>
      </span>
    </div>
  );
}
