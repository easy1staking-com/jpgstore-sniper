"use client";

import { useCallback, useEffect, useState } from "react";
import { useWallet } from "@meshsdk/react";
import { CollectionInfo, ContractInfo, FeeBreakdown, Settings, SnipeOrder } from "@/lib/types";
import { adaToLovelace, calculateFees, formatAda } from "@/lib/fees";
import { fetchCollection } from "@/lib/api";
import { createSnipeTx } from "@/lib/transactions";
import { txExplorerUrl, isContractReady } from "@/lib/contract";
import SnipeReceipt from "./SnipeReceipt";

interface CollectionSnipeFormProps {
  onCreated: (order: SnipeOrder) => void;
  settings: Settings;
  contracts: ContractInfo | null;
}

export default function CollectionSnipeForm({
  onCreated,
  settings,
  contracts,
}: CollectionSnipeFormProps) {
  const { wallet } = useWallet();

  const [policyId, setPolicyId] = useState("");
  const [collection, setCollection] = useState<CollectionInfo | null>(null);
  const [collectionLoading, setCollectionLoading] = useState(false);
  const [collectionError, setCollectionError] = useState<string | null>(null);

  const [maxPriceAda, setMaxPriceAda] = useState("");
  const [quantity, setQuantity] = useState(1);

  const [showReceipt, setShowReceipt] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [txHash, setTxHash] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const maxPriceLovelace = maxPriceAda
    ? adaToLovelace(parseFloat(maxPriceAda))
    : 0;
  const fees: FeeBreakdown | null =
    maxPriceLovelace > 0
      ? calculateFees(settings, maxPriceLovelace)
      : null;

  const lookupCollection = useCallback(async () => {
    const trimmed = policyId.trim();
    if (trimmed.length !== 56) {
      setCollection(null);
      if (trimmed.length > 0)
        setCollectionError("Policy ID must be 56 hex characters");
      return;
    }
    setCollectionLoading(true);
    setCollectionError(null);
    try {
      const info = await fetchCollection(trimmed);
      setCollection(info);
    } catch {
      setCollectionError(
        "Collection not found — you can still create a snipe"
      );
      setCollection({
        policyId: trimmed,
        name: "Unknown Collection",
        description: "",
      });
    } finally {
      setCollectionLoading(false);
    }
  }, [policyId]);

  useEffect(() => {
    if (policyId.trim().length === 56) {
      const timeout = setTimeout(lookupCollection, 500);
      return () => clearTimeout(timeout);
    } else {
      setCollection(null);
      setCollectionError(null);
    }
  }, [policyId, lookupCollection]);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!fees || !collection) return;
    setErrorMsg(null);
    setShowReceipt(true);
  }

  async function handleConfirm() {
    if (!fees || !collection || !wallet) return;

    setSubmitting(true);
    setErrorMsg(null);
    try {
      if (isContractReady(contracts)) {
        // Real on-chain transaction
        const result = await createSnipeTx({
          wallet,
          contracts: contracts!,
          settings,
          targetPolicyId: policyId.trim(),
          maxPriceLovelace,
          quantity,
        });

        setTxHash(result.txHash);
        setSuccessMsg(
          `Snipe${quantity > 1 ? "s" : ""} submitted!`
        );
      } else {
        // No contract configured — add to local state only
        for (let i = 0; i < quantity; i++) {
          const order: SnipeOrder = {
            id: `snipe-${Date.now()}-${i}`,
            policyId: policyId.trim(),
            collectionName: collection.name,
            maxPrice: maxPriceLovelace,
            operatorFee: fees.operatorFee,
            protocolFee: fees.protocolFee,
            txFeeBudget: fees.txFeeBudget,
            totalLocked: fees.totalLocked,
            status: "active",
            createdAt: new Date().toISOString(),
          };
          onCreated(order);
        }
        setSuccessMsg(
          `Created ${quantity} snipe${quantity > 1 ? "s" : ""} for ${collection.name}!`
        );
      }

      setShowReceipt(false);
      setPolicyId("");
      setMaxPriceAda("");
      setQuantity(1);
      setCollection(null);

      setTimeout(() => {
        setSuccessMsg(null);
        setTxHash(null);
      }, 8000);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Transaction failed";
      // Don't alert for user rejections
      if (message.includes("User declined") || message.includes("rejected")) {
        setShowReceipt(false);
      } else {
        setErrorMsg(message);
      }
    } finally {
      setSubmitting(false);
    }
  }

  const canSubmit =
    collection !== null && maxPriceLovelace >= 1_000_000 && quantity >= 1;

  return (
    <>
      <form onSubmit={handleSubmit} className="space-y-6">
        {/* Policy ID */}
        <fieldset>
          <label className="mb-2 flex items-center gap-2 text-sm font-medium text-text-mid">
            <svg viewBox="0 0 16 16" className="h-3.5 w-3.5 text-text-dim" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
              <path d="M2 4 L8 2 L14 4 L14 12 L8 14 L2 12 Z" />
            </svg>
            Policy ID
          </label>
          <input
            type="text"
            value={policyId}
            onChange={(e) => setPolicyId(e.target.value)}
            placeholder="56-character hex policy ID"
            className="w-full rounded-lg border border-border-bright bg-surface px-4 py-3 font-mono text-sm text-text placeholder:text-text-dim transition-colors"
          />
          {collectionLoading && (
            <p className="mt-2 flex items-center gap-2 text-xs text-cyan">
              <span className="inline-block h-1.5 w-1.5 rounded-full bg-cyan animate-pulse" />
              Looking up collection...
            </p>
          )}
          {collectionError && (
            <p className="mt-2 text-xs text-amber">{collectionError}</p>
          )}
          {collection && !collectionLoading && (
            <div className="mt-2 rounded-lg border border-neon/20 bg-neon/[0.04] px-4 py-2.5 animate-fade-in">
              <p className="text-sm font-medium text-neon">
                {collection.name}
              </p>
              {collection.description && (
                <p className="mt-0.5 text-xs text-text-dim line-clamp-2">
                  {collection.description}
                </p>
              )}
            </div>
          )}
        </fieldset>

        {/* Max Price */}
        <fieldset>
          <label className="mb-2 flex items-center gap-2 text-sm font-medium text-text-mid">
            <svg viewBox="0 0 16 16" className="h-3.5 w-3.5 text-text-dim" fill="none" stroke="currentColor" strokeWidth="1.5">
              <circle cx="8" cy="8" r="6" />
              <path d="M8 5v6M6 7h4M6 9h4" strokeLinecap="round" />
            </svg>
            Max Price
            <span className="text-xs text-text-dim font-normal">(ADA)</span>
          </label>
          <input
            type="number"
            min="1"
            step="0.000001"
            value={maxPriceAda}
            onChange={(e) => setMaxPriceAda(e.target.value)}
            placeholder="e.g. 45"
            className="w-full rounded-lg border border-border-bright bg-surface px-4 py-3 font-mono text-sm text-text placeholder:text-text-dim transition-colors"
          />
        </fieldset>

        {/* Quantity */}
        <fieldset>
          <label className="mb-1 flex items-center gap-2 text-sm font-medium text-text-mid">
            <svg viewBox="0 0 16 16" className="h-3.5 w-3.5 text-text-dim" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
              <rect x="2" y="6" width="4" height="8" rx="1" />
              <rect x="6" y="3" width="4" height="11" rx="1" />
              <rect x="10" y="6" width="4" height="8" rx="1" />
            </svg>
            Quantity
            <span className="text-xs text-text-dim font-normal">(1-20)</span>
          </label>
          <p className="mb-2 text-xs text-text-dim">
            How many NFTs to snipe from this collection
          </p>
          <input
            type="number"
            min="1"
            max="20"
            value={quantity}
            onChange={(e) =>
              setQuantity(
                Math.max(1, Math.min(20, parseInt(e.target.value) || 1))
              )
            }
            className="w-28 rounded-lg border border-border-bright bg-surface px-4 py-3 font-mono text-sm text-text transition-colors"
          />
        </fieldset>

        {/* Live Fee Preview */}
        {fees && (
          <div className="rounded-lg border border-border bg-bg p-5 animate-fade-in">
            <p className="mb-3 text-[11px] font-semibold uppercase tracking-[0.15em] text-text-dim">
              Fee Preview
              <span className="text-text-dim/50 ml-1.5 font-normal normal-case tracking-normal">
                per snipe
              </span>
            </p>
            <div className="space-y-2 text-sm">
              <PreviewRow label="Max Price" value={formatAda(maxPriceLovelace)} />
              <PreviewRow
                label={`Operator Fee (${settings.operatorFeePct}%)`}
                value={formatAda(fees.operatorFee)}
              />
              <PreviewRow
                label={`Protocol Fee (${settings.protocolFeePct}%)`}
                value={formatAda(fees.protocolFee)}
              />
              <PreviewRow label="Tx Fee Budget" value={formatAda(fees.txFeeBudget)} />

              <div className="flex items-center justify-between border-t border-border pt-2.5 mt-3">
                <span className="text-xs uppercase tracking-wider text-text-dim font-medium">
                  Locked per snipe
                </span>
                <span className="font-mono text-sm font-semibold text-neon">
                  {formatAda(fees.totalLocked)}{" "}
                  <span className="text-neon-dim text-xs">ADA</span>
                </span>
              </div>
            </div>
            {quantity > 1 && (
              <div className="mt-3 flex items-center justify-between border-t border-border pt-2.5">
                <span className="text-xs uppercase tracking-wider text-text-dim font-medium">
                  Total ({quantity} snipes)
                </span>
                <span className="font-mono text-sm font-semibold text-neon">
                  {formatAda(fees.totalLocked * quantity)}{" "}
                  <span className="text-neon-dim text-xs">ADA</span>
                </span>
              </div>
            )}
          </div>
        )}

        {/* Error */}
        {errorMsg && (
          <div className="rounded-lg border border-red/30 bg-red/10 px-4 py-3 text-sm text-red animate-fade-in">
            {errorMsg}
          </div>
        )}

        {/* Submit */}
        <button
          type="submit"
          disabled={!canSubmit}
          className={`
            w-full rounded-lg px-4 py-3.5 text-sm font-semibold tracking-wide uppercase
            transition-all duration-200
            ${
              canSubmit
                ? "bg-neon text-void hover:bg-neon-dim shadow-[0_0_20px_var(--color-neon-glow)] hover:shadow-[0_0_30px_var(--color-neon-glow)]"
                : "bg-surface-light text-text-dim border border-border cursor-not-allowed"
            }
          `}
        >
          Create Snipe
        </button>
      </form>

      {/* Success Toast */}
      {successMsg && (
        <div className="fixed bottom-6 right-6 z-50 flex items-center gap-2.5 rounded-lg border border-neon/30 bg-surface px-4 py-3 text-sm font-medium text-neon shadow-[0_0_20px_rgba(0,255,136,0.1)] animate-toast">
          <svg viewBox="0 0 16 16" className="h-4 w-4 shrink-0" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
            <polyline points="3,8 7,12 13,4" />
          </svg>
          <span>{successMsg}</span>
          {txHash && (
            <a
              href={txExplorerUrl(txHash, contracts?.networkId ?? 1)}
              target="_blank"
              rel="noopener noreferrer"
              className="ml-1 underline underline-offset-2 text-neon-dim hover:text-neon"
            >
              View tx
            </a>
          )}
        </div>
      )}

      {/* Receipt Modal */}
      {showReceipt && collection && fees && (
        <SnipeReceipt
          collection={collection}
          maxPrice={maxPriceLovelace}
          quantity={quantity}
          fees={fees}
          settings={settings}
          submitting={submitting}
          onCancel={() => setShowReceipt(false)}
          onConfirm={handleConfirm}
        />
      )}
    </>
  );
}

function PreviewRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-text-dim text-[13px]">{label}</span>
      <span className="font-mono text-text-mid text-[13px]">
        {value} <span className="text-text-dim text-[11px]">ADA</span>
      </span>
    </div>
  );
}
