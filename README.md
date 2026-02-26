# JPG.Store Sniper

A decentralized NFT sniping platform for [jpg.store](https://www.jpg.store) on Cardano. Users lock ADA into a smart contract defining what they want to snipe. Decentralized bot operators monitor the chain and execute matching purchases, earning fees for their service.

No trust required — the smart contract is the arbiter.

## How It Works

```
User creates snipe order         Bot detects matching listing       Smart contract validates
         |                                |                                 |
  Lock ADA + conditions  ──>   Build purchase tx   ──>   NFT to user, fees to operator
  at escrow contract            (escrow + marketplace)        & protocol treasury
```

1. **User** connects wallet, picks a collection (or specific NFTs), sets a max price, and signs a transaction that locks ADA at the escrow script address.
2. **Bot** streams new blocks via a Cardano node, detects new jpg.store listings, matches them against active snipe orders.
3. **Smart contract** enforces: NFT delivered to user, operator fee paid, protocol fee paid, listing NFT burned. No one can steal the locked funds.

## Architecture

Monorepo with two components (+ a frontend, WIP):

```
jpgstore-sniper/
├── src/
│   ├── jpgstore-sniper-onchain/    # Aiken smart contracts (Plutus V3)
│   └── jpgstore-sniper-offchain/   # Java bot (Spring Boot + Yaci)
└── LICENSE                         # Apache 2.0
```

### Smart Contracts — `src/jpgstore-sniper-onchain/`

Written in [Aiken](https://aiken-lang.org/) targeting Plutus V3. Four validators chained linearly (no circular dependencies):

| # | Validator | Type | Purpose |
|---|-----------|------|---------|
| 1 | **Settings NFT Policy** | Minting (one-shot) | Unique identifier for the settings UTxO |
| 2 | **Settings Validator** | Spending | Guards protocol settings (fees, treasury, admin). Continuous state, NFT-gated |
| 3 | **Listing NFT Policy** | Minting (shared) | Proof-of-valid-listing. Validates datum + locked amount at mint time |
| 4 | **Snipe Escrow** | Spending | Execute (NFT delivery + fees) or Cancel (owner signature) |

Two snipe strategies:
- **Policy ID snipe** — any NFT from a given collection
- **Merkle tree snipe** — a specific list of NFTs (compressed into a merkle root)

Key design decisions:
- Fees computed as absolute lovelace at listing time, stored in datum. Escrow just checks outputs.
- Listing NFT burned on Execute and Cancel (prevents reuse with bogus fees).
- Double satisfaction prevented via `hash(snipe_utxo_ref)` tags on outputs.
- Batching supported — multiple snipes executable in a single transaction.

### Bot — `src/jpgstore-sniper-offchain/`

Java 21 + Spring Boot application that:
- Streams blocks from a Cardano node via [Yaci Store](https://github.com/bloxbean/yaci-store)
- Detects new snipe orders and jpg.store listings
- Matches listings against active snipes (in-memory registry)
- Builds and submits purchase transactions via [Cardano Client Lib](https://github.com/bloxbean/cardano-client-lib)
- Persists merkle snipe NFT lists to PostgreSQL (needed to reconstruct proofs)

## Prerequisites

- **Aiken** v1.1.21+ — [install guide](https://aiken-lang.org/installation-instructions)
- **Java** 21+
- **PostgreSQL** 14+
- **Cardano node** (or access to one) — for block streaming
- **Blockfrost API key** — for UTxO queries and tx submission

## Getting Started

### Smart Contracts

```bash
cd src/jpgstore-sniper-onchain

# Build validators (generates plutus.json)
aiken build

# Run tests (43 tests)
aiken check
```

The generated `plutus.json` contains the compiled Plutus scripts used by the bot.

### Bot

```bash
cd src/jpgstore-sniper-offchain

# Configure environment
cp .env.dev .env
# Edit .env with your:
#   - Wallet mnemonic
#   - Blockfrost API key
#   - Cardano node host/port

# Build
./gradlew build

# Run
./gradlew bootRun
```

The bot requires a running PostgreSQL instance. Default connection: `jdbc:postgresql://localhost:5432/jpgsniper` (configurable in `application.yaml`).

### Database

The bot uses Flyway for schema migrations. Tables are created automatically on startup:

| Table | Purpose |
|-------|---------|
| `nft_collection` | Cached collection metadata (policy ID, name, description) |
| `nft_token` | Cached token metadata (asset ID, name, image) |
| `merkle_snipe` | NFT lists for merkle tree snipes (needed to build proofs) |

## Fee Model

Fees are configured in the on-chain settings datum and enforced by the smart contract:

| Parameter | Description |
|-----------|-------------|
| `operator_fee_pct` | Percentage fee for the bot operator |
| `protocol_fee_pct` | Percentage fee for the protocol |
| `min_operator_fee` | Floor for operator fee (lovelace) |
| `min_protocol_fee` | Floor for protocol fee (lovelace) |
| `tx_fee_budget` | Reserved for Cardano tx fees (lovelace) |

Formula per snipe: `total_locked = max_price + operator_fee + protocol_fee + tx_fee_budget`

Fees are computed at listing time as `max(ceil(max_price * pct / 100), min_fee)` and stored as absolute values in the datum. The escrow validator only enforces protocol minimums — the bot keeps any excess after paying the NFT seller and marketplace fees.

## Running a Bot

The bot is designed to be decentralized — anyone can run an instance. The smart contract guarantees correct behavior regardless of who operates the bot. Competition between operators means faster execution for users.

Bot operators earn the operator fee on every successful snipe execution.

## License

[Apache License 2.0](LICENSE)

Built by [easy1staking.com](https://www.easy1staking.com) | EASY1 Stake Pool
