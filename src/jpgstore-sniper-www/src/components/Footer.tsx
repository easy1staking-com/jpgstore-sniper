export default function Footer() {
  return (
    <footer className="relative border-t border-border py-5">
      {/* bottom accent line */}
      <div className="absolute inset-x-0 top-0 h-[1px] bg-gradient-to-r from-transparent via-neon/20 to-transparent" />

      <div className="mx-auto max-w-6xl px-5 flex items-center justify-between">
        <p className="text-xs text-text-dim tracking-wide">
          Built by{" "}
          <a
            href="https://easy1staking.com"
            target="_blank"
            rel="noopener noreferrer"
            className="text-text-mid hover:text-neon transition-colors"
          >
            easy1staking.com
          </a>
          <span className="mx-2 text-border-bright">|</span>
          <a
            href="https://pool.pm/EASY1"
            target="_blank"
            rel="noopener noreferrer"
            className="text-text-mid hover:text-neon transition-colors"
          >
            EASY1 Stake Pool
          </a>
        </p>
        <p className="text-[10px] font-mono text-text-dim tracking-widest uppercase">
          Cardano
        </p>
      </div>
    </footer>
  );
}
