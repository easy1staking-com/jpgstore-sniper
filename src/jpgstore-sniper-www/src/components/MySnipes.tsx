"use client";

import { useState } from "react";
import { useWallet } from "@meshsdk/react";
import { SnipeOrder } from "@/lib/types";
import { cancelSnipeTx } from "@/lib/transactions";
import { txExplorerUrl, LISTING_NFT_POLICY_SCRIPT_CBOR } from "@/lib/contract";
import SnipeCard from "./SnipeCard";

interface MySnipesProps {
  snipes: SnipeOrder[];
  loading: boolean;
  error: string | null;
  onCancel: (ids: string[]) => void;
  onRefresh: () => void;
}

export default function MySnipes({ snipes, loading, error, onCancel, onRefresh }: MySnipesProps) {
  const { wallet } = useWallet();
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [cancelling, setCancelling] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [txHash, setTxHash] = useState<string | null>(null);

  const activeSnipes = snipes.filter((s) => s.status === "active");

  function toggleOne(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function toggleAll() {
    if (selected.size === activeSnipes.length) {
      setSelected(new Set());
    } else {
      setSelected(new Set(activeSnipes.map((s) => s.id)));
    }
  }

  async function handleCancel() {
    const ids = Array.from(selected);
    const selectedSnipes = activeSnipes.filter((s) => ids.includes(s.id));

    setCancelling(true);
    setCancelError(null);
    try {
      if (LISTING_NFT_POLICY_SCRIPT_CBOR && wallet) {
        // Real on-chain cancel transaction
        const utxos = selectedSnipes
          .filter((s) => s.txHash && s.outputIndex !== undefined)
          .map((s) => ({
            txHash: s.txHash!,
            outputIndex: s.outputIndex!,
          }));

        if (utxos.length > 0) {
          const result = await cancelSnipeTx({ wallet, snipeUtxos: utxos });
          setTxHash(result.txHash);
          setSuccessMsg(`Cancel tx submitted!`);
          setTimeout(() => {
            setSuccessMsg(null);
            setTxHash(null);
          }, 8000);
        }
      }

      // Update local state
      onCancel(ids);
      setSelected(new Set());

      // Refresh from API after a delay to let the tx propagate
      setTimeout(onRefresh, 5000);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Cancel failed";
      if (!message.includes("User declined") && !message.includes("rejected")) {
        setCancelError(message);
      }
    } finally {
      setCancelling(false);
    }
  }

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center py-24 text-center animate-fade-in">
        <div className="h-8 w-8 mb-4 rounded-full border-2 border-neon/30 border-t-neon animate-spin" />
        <p className="text-sm text-text-mid">Loading snipes...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center py-24 text-center animate-fade-in">
        <svg viewBox="0 0 48 48" className="h-16 w-16 mb-5" fill="none" stroke="currentColor" strokeWidth="1.2">
          <circle cx="24" cy="24" r="16" className="stroke-red/50" />
          <line x1="18" y1="18" x2="30" y2="30" className="stroke-red" strokeWidth="2" strokeLinecap="round" />
          <line x1="30" y1="18" x2="18" y2="30" className="stroke-red" strokeWidth="2" strokeLinecap="round" />
        </svg>
        <p className="text-base font-medium text-red">{error}</p>
        <button
          onClick={onRefresh}
          className="mt-4 rounded-lg border border-border-bright px-4 py-2 text-sm font-medium text-text-mid transition-all hover:bg-surface-light hover:text-text"
        >
          Retry
        </button>
      </div>
    );
  }

  if (activeSnipes.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-24 text-center animate-fade-in">
        <svg viewBox="0 0 48 48" className="h-16 w-16 mb-5 text-text-dim" fill="none" stroke="currentColor" strokeWidth="1">
          <circle cx="24" cy="24" r="16" className="stroke-border-bright" />
          <circle cx="24" cy="24" r="6" className="stroke-border-bright" />
          <line x1="24" y1="4" x2="24" y2="8" className="stroke-border" />
          <line x1="24" y1="40" x2="24" y2="44" className="stroke-border" />
          <line x1="4" y1="24" x2="8" y2="24" className="stroke-border" />
          <line x1="40" y1="24" x2="44" y2="24" className="stroke-border" />
        </svg>
        <p className="text-base font-medium text-text-mid">No active snipes</p>
        <p className="mt-1.5 text-sm text-text-dim">
          Create your first snipe to start tracking
        </p>
      </div>
    );
  }

  return (
    <div>
      {/* Toolbar */}
      <div className="mb-5 flex items-center justify-between rounded-lg border border-border bg-surface px-4 py-3">
        <label className="flex items-center gap-2.5 text-sm text-text-mid cursor-pointer select-none">
          <input
            type="checkbox"
            checked={selected.size === activeSnipes.length && activeSnipes.length > 0}
            onChange={toggleAll}
          />
          <span>
            Select all{" "}
            <span className="font-mono text-text-dim text-xs">
              ({activeSnipes.length})
            </span>
          </span>
        </label>

        <div className="flex items-center gap-3">
          {selected.size > 0 && (
            <span className="text-xs font-mono text-text-dim">
              {selected.size} selected
            </span>
          )}
          <button
            onClick={handleCancel}
            disabled={selected.size === 0 || cancelling}
            className={`
              rounded-md px-4 py-2 text-sm font-medium transition-all duration-200
              ${
                selected.size > 0 && !cancelling
                  ? "bg-red/10 text-red border border-red/30 hover:bg-red/20 hover:border-red/50"
                  : "bg-surface-light text-text-dim border border-border cursor-not-allowed"
              }
            `}
          >
            {cancelling ? (
              <span className="flex items-center gap-2">
                <span className="inline-block h-3.5 w-3.5 rounded-full border-2 border-red/30 border-t-red animate-spin" />
                Cancelling...
              </span>
            ) : (
              "Cancel Selected"
            )}
          </button>
        </div>
      </div>

      {/* Cancel Error */}
      {cancelError && (
        <div className="mb-4 rounded-lg border border-red/30 bg-red/10 px-4 py-3 text-sm text-red animate-fade-in">
          {cancelError}
        </div>
      )}

      {/* Cards */}
      <div className="grid gap-3">
        {activeSnipes.map((order, i) => (
          <SnipeCard
            key={order.id}
            order={order}
            selected={selected.has(order.id)}
            onToggle={toggleOne}
            index={i}
          />
        ))}
      </div>

      {/* Success Toast */}
      {successMsg && (
        <div className="fixed bottom-6 right-6 z-50 flex items-center gap-2.5 rounded-lg border border-neon/30 bg-surface px-4 py-3 text-sm font-medium text-neon shadow-[0_0_20px_rgba(0,255,136,0.1)] animate-toast">
          <svg viewBox="0 0 16 16" className="h-4 w-4 shrink-0" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
            <polyline points="3,8 7,12 13,4" />
          </svg>
          <span>{successMsg}</span>
          {txHash && (
            <a
              href={txExplorerUrl(txHash)}
              target="_blank"
              rel="noopener noreferrer"
              className="ml-1 underline underline-offset-2 text-neon-dim hover:text-neon"
            >
              View tx
            </a>
          )}
        </div>
      )}
    </div>
  );
}
