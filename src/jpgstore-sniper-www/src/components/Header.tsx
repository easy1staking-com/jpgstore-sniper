"use client";

import { CardanoWallet } from "@meshsdk/react";

type Tab = "my-snipes" | "new-snipe";

interface HeaderProps {
  activeTab: Tab;
  onTabChange: (tab: Tab) => void;
}

export default function Header({ activeTab, onTabChange }: HeaderProps) {
  return (
    <header className="relative border-b border-border bg-bg/80 backdrop-blur-md">
      {/* top accent line */}
      <div className="absolute inset-x-0 top-0 h-[1px] bg-gradient-to-r from-transparent via-neon/40 to-transparent" />

      <div className="mx-auto flex max-w-6xl items-center justify-between px-5 py-3.5">
        {/* Logo + Nav */}
        <div className="flex items-center gap-10">
          {/* Logo */}
          <div className="flex items-center gap-2.5">
            {/* crosshair icon */}
            <div className="relative flex h-8 w-8 items-center justify-center">
              <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="1.5">
                <circle cx="12" cy="12" r="8" className="stroke-neon" />
                <circle cx="12" cy="12" r="3" className="stroke-neon" />
                <line x1="12" y1="0" x2="12" y2="4" className="stroke-neon/50" />
                <line x1="12" y1="20" x2="12" y2="24" className="stroke-neon/50" />
                <line x1="0" y1="12" x2="4" y2="12" className="stroke-neon/50" />
                <line x1="20" y1="12" x2="24" y2="12" className="stroke-neon/50" />
              </svg>
            </div>
            <h1 className="text-lg font-semibold tracking-wide">
              <span className="text-neon">JPG</span>
              <span className="text-text-mid mx-0.5">/</span>
              <span className="text-text font-light tracking-widest uppercase text-sm">Sniper</span>
            </h1>
          </div>

          {/* Nav Tabs */}
          <nav className="flex items-center rounded-lg border border-border bg-surface p-1 gap-1">
            <TabButton
              active={activeTab === "my-snipes"}
              onClick={() => onTabChange("my-snipes")}
              label="My Snipes"
              icon={
                <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
                  <rect x="2" y="2" width="5" height="5" rx="1" />
                  <rect x="9" y="2" width="5" height="5" rx="1" />
                  <rect x="2" y="9" width="5" height="5" rx="1" />
                  <rect x="9" y="9" width="5" height="5" rx="1" />
                </svg>
              }
            />
            <TabButton
              active={activeTab === "new-snipe"}
              onClick={() => onTabChange("new-snipe")}
              label="New Snipe"
              icon={
                <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
                  <line x1="8" y1="3" x2="8" y2="13" />
                  <line x1="3" y1="8" x2="13" y2="8" />
                </svg>
              }
            />
          </nav>
        </div>

        {/* Wallet */}
        <CardanoWallet />
      </div>
    </header>
  );
}

function TabButton({
  active,
  onClick,
  label,
  icon,
}: {
  active: boolean;
  onClick: () => void;
  label: string;
  icon: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      className={`
        relative flex items-center gap-2 rounded-md px-4 py-2 text-sm font-medium
        transition-all duration-200
        ${
          active
            ? "bg-neon/15 text-neon shadow-[inset_0_0_12px_rgba(0,255,136,0.06)]"
            : "text-text-mid hover:text-text hover:bg-surface-light"
        }
      `}
    >
      <span className={active ? "text-neon" : "text-text-dim"}>{icon}</span>
      {label}
      {active && (
        <span className="absolute bottom-0 left-3 right-3 h-[2px] rounded-full bg-neon shadow-[0_0_6px_var(--color-neon-glow)]" />
      )}
    </button>
  );
}
