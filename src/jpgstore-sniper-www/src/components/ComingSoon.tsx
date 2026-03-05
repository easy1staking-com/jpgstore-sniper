export default function ComingSoon() {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-border bg-surface py-20 text-center animate-fade-in">
      <svg viewBox="0 0 48 48" className="h-14 w-14 mb-5" fill="none" stroke="currentColor" strokeWidth="1.2">
        <rect x="8" y="12" width="32" height="24" rx="3" className="stroke-border-bright" />
        <circle cx="24" cy="24" r="5" className="stroke-text-dim" />
        <line x1="24" y1="19" x2="24" y2="24" className="stroke-text-dim" strokeLinecap="round" />
        <line x1="24" y1="24" x2="27" y2="22" className="stroke-text-dim" strokeLinecap="round" />
      </svg>
      <h3 className="text-base font-semibold text-text">
        Multi-NFT Snipe
      </h3>
      <p className="mt-2 max-w-xs text-sm text-text-dim leading-relaxed">
        On-chain contracts for targeted multi-NFT sniping are in development
      </p>
      <span className="mt-4 rounded border border-amber/30 bg-amber/10 px-3 py-1 text-[11px] font-semibold uppercase tracking-wider text-amber">
        Coming Soon
      </span>
    </div>
  );
}
