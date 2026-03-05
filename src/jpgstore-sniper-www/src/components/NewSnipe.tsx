"use client";

import { useState } from "react";
import { ContractInfo, Settings, SnipeOrder } from "@/lib/types";
import CollectionSnipeForm from "./CollectionSnipeForm";
import ComingSoon from "./ComingSoon";

type SubTab = "collection" | "multi-nft";

interface NewSnipeProps {
  onCreated: (order: SnipeOrder) => void;
  walletConnected: boolean;
  settings: Settings | null;
  settingsLoading: boolean;
  settingsError: string | null;
  contracts: ContractInfo | null;
}

export default function NewSnipe({ onCreated, walletConnected, settings, settingsLoading, settingsError, contracts }: NewSnipeProps) {
  const [subTab, setSubTab] = useState<SubTab>("collection");

  if (!walletConnected) {
    return (
      <div className="flex flex-col items-center justify-center py-24 text-center animate-fade-in">
        <svg viewBox="0 0 48 48" className="h-16 w-16 mb-5" fill="none" stroke="currentColor" strokeWidth="1.2">
          <rect x="8" y="16" width="32" height="22" rx="3" className="stroke-border-bright" />
          <path d="M8 22 h32" className="stroke-border" />
          <circle cx="24" cy="31" r="3" className="stroke-text-dim" />
        </svg>
        <p className="text-base font-medium text-text-mid">
          Wallet not connected
        </p>
        <p className="mt-1.5 text-sm text-text-dim">
          Connect your Cardano wallet to create snipes
        </p>
      </div>
    );
  }

  if (settingsLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-24 text-center animate-fade-in">
        <div className="h-8 w-8 mb-4 rounded-full border-2 border-neon/30 border-t-neon animate-spin" />
        <p className="text-sm text-text-mid">Loading settings...</p>
      </div>
    );
  }

  if (settingsError || !settings) {
    return (
      <div className="flex flex-col items-center justify-center py-24 text-center animate-fade-in">
        <svg viewBox="0 0 48 48" className="h-16 w-16 mb-5" fill="none" stroke="currentColor" strokeWidth="1.2">
          <circle cx="24" cy="24" r="16" className="stroke-red/50" />
          <line x1="18" y1="18" x2="30" y2="30" className="stroke-red" strokeWidth="2" strokeLinecap="round" />
          <line x1="30" y1="18" x2="18" y2="30" className="stroke-red" strokeWidth="2" strokeLinecap="round" />
        </svg>
        <p className="text-base font-medium text-red">
          Could not load settings
        </p>
        <p className="mt-1.5 text-sm text-text-dim">
          {settingsError || "The on-chain settings could not be read. Try again later."}
        </p>
      </div>
    );
  }

  return (
    <div className="animate-fade-in">
      {/* Sub-tabs */}
      <div className="mb-8 flex items-center gap-1 rounded-lg border border-border bg-surface p-1 w-fit">
        <button
          onClick={() => setSubTab("collection")}
          className={`
            relative flex items-center gap-2 rounded-md px-5 py-2.5 text-sm font-medium
            transition-all duration-200
            ${
              subTab === "collection"
                ? "bg-neon/15 text-neon shadow-[inset_0_0_12px_rgba(0,255,136,0.06)]"
                : "text-text-mid hover:text-text hover:bg-surface-light"
            }
          `}
        >
          <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
            <rect x="2" y="2" width="12" height="12" rx="2" />
            <line x1="6" y1="2" x2="6" y2="14" />
          </svg>
          Collection Snipe
          {subTab === "collection" && (
            <span className="absolute bottom-0 left-3 right-3 h-[2px] rounded-full bg-neon shadow-[0_0_6px_var(--color-neon-glow)]" />
          )}
        </button>

        <button
          onClick={() => setSubTab("multi-nft")}
          className={`
            relative flex items-center gap-2 rounded-md px-5 py-2.5 text-sm font-medium
            transition-all duration-200
            ${
              subTab === "multi-nft"
                ? "bg-neon/15 text-neon shadow-[inset_0_0_12px_rgba(0,255,136,0.06)]"
                : "text-text-mid hover:text-text hover:bg-surface-light"
            }
          `}
        >
          <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
            <rect x="1" y="4" width="8" height="8" rx="1.5" />
            <rect x="5" y="2" width="8" height="8" rx="1.5" />
          </svg>
          Multi-NFT Snipe
          <span className="rounded border border-amber/30 bg-amber/10 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wider text-amber">
            Soon
          </span>
          {subTab === "multi-nft" && (
            <span className="absolute bottom-0 left-3 right-3 h-[2px] rounded-full bg-neon shadow-[0_0_6px_var(--color-neon-glow)]" />
          )}
        </button>
      </div>

      {/* Content */}
      <div className="mx-auto max-w-lg">
        {subTab === "collection" ? (
          <CollectionSnipeForm onCreated={onCreated} settings={settings} contracts={contracts} />
        ) : (
          <ComingSoon />
        )}
      </div>
    </div>
  );
}
