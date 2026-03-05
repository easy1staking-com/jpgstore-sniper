import {
  LISTING_NFT_POLICY_SCRIPT_CBOR,
  LISTING_NFT_POLICY_ID,
  ESCROW_SCRIPT_ADDRESS,
  SETTINGS_UTXO_TX_HASH,
  SETTINGS_UTXO_OUTPUT_INDEX,
} from "./contract";
import { Settings, FeeBreakdown } from "./types";
import { calculateFees } from "./fees";
// Use generic type for wallet — MeshJS useWallet() returns MeshCardanoBrowserWallet
// which has getUsedAddresses, getUtxos, signTx, submitTx
interface WalletLike {
  getUsedAddresses(): Promise<string[]>;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  getUtxos(): Promise<any[]>;
  signTx(unsignedTx: string, partialSign?: boolean): Promise<string>;
  submitTx(signedTx: string): Promise<string>;
}

/**
 * Build inline datum for a Policy ID snipe (Constr 0).
 *
 * PolicySnipeDatum {
 *   owner_pkh: ByteArray,
 *   nft_destination: Address,
 *   policy_id: ByteArray,
 *   max_price: Int,
 *   protocol_fee: Int,
 * }
 */
function buildPolicySnipeDatumCbor(
  ownerPkh: string,
  destinationAddress: string,
  targetPolicyId: string,
  maxPrice: number,
  protocolFee: number
): string {
  // Use MeshJS Data constructors for CBOR encoding
  // This will be called from MeshSDK's tx builder which handles CBOR
  // For now we construct the JSON representation that MeshJS accepts
  return JSON.stringify({
    constructor: 0,
    fields: [
      { bytes: ownerPkh },
      // Address as constructor: base address = Constr(0, [Constr(0, [pkh]), Constr(0, [Constr(0, [stake_pkh])])])
      // We'll use the resolvePaymentKeyHash approach instead
      addressToPlutusFields(destinationAddress),
      { bytes: targetPolicyId },
      { int: maxPrice },
      { int: protocolFee },
    ],
  });
}

/**
 * Parse a Cardano address into its Plutus Data representation.
 * Base address: Constr(0, [Constr(type, [pkh]), Constr(0, [Constr(type, [stake_hash])])])
 * Enterprise address: Constr(1, [Constr(type, [pkh])])
 */
function addressToPlutusFields(bech32Address: string): object {
  // MeshJS resolvePaymentKeyHash and resolveStakeKeyHash handle this
  // For now we pass the raw address and let the caller use MeshJS utilities
  // This is a placeholder — real implementation will use @meshsdk/core utilities
  return { alternative: 0, fields: [bech32Address] };
}

export interface CreateSnipeParams {
  wallet: WalletLike;
  settings: Settings;
  targetPolicyId: string;
  maxPriceLovelace: number;
  quantity: number;
}

export interface CreateSnipeResult {
  txHash: string;
}

/**
 * Build and submit a "Create Snipe" transaction using MeshJS.
 *
 * For each snipe:
 * 1. Mint 1 listing NFT via the listing mint policy
 * 2. Lock (maxPrice + operatorFee + protocolFee + txFeeBudget) to escrow with inline datum
 *
 * The settings UTxO is added as a reference input.
 */
export async function createSnipeTx(
  params: CreateSnipeParams
): Promise<CreateSnipeResult> {
  const { wallet, settings, targetPolicyId, maxPriceLovelace, quantity } =
    params;

  // Lazy import to avoid SSR issues with WASM
  const { MeshTxBuilder, resolvePaymentKeyHash, resolveStakeKeyHash } =
    await import("@meshsdk/core");

  const fees: FeeBreakdown = calculateFees(settings, maxPriceLovelace);
  const lockedAmount =
    maxPriceLovelace +
    fees.operatorFee +
    fees.protocolFee +
    fees.txFeeBudget;

  const usedAddresses = await wallet.getUsedAddresses();
  if (!usedAddresses.length) throw new Error("No wallet address found");
  const changeAddress = usedAddresses[0];
  const ownerPkh = resolvePaymentKeyHash(changeAddress);
  const stakePkh = resolveStakeKeyHash(changeAddress);

  const utxos = await wallet.getUtxos();

  const txBuilder = new MeshTxBuilder();

  // Reference input: settings UTxO
  if (SETTINGS_UTXO_TX_HASH) {
    txBuilder.readOnlyTxInReference(
      SETTINGS_UTXO_TX_HASH,
      SETTINGS_UTXO_OUTPUT_INDEX
    );
  }

  for (let i = 0; i < quantity; i++) {
    // Build inline datum
    const datumFields = [
      // owner_pkh
      ownerPkh,
      // nft_destination — base address or enterprise
      stakePkh
        ? {
            constructor: 0,
            fields: [
              { constructor: 0, fields: [ownerPkh] },
              {
                constructor: 0,
                fields: [
                  { constructor: 0, fields: [{ constructor: 0, fields: [stakePkh] }] },
                ],
              },
            ],
          }
        : {
            constructor: 1,
            fields: [{ constructor: 0, fields: [ownerPkh] }],
          },
      // policy_id
      targetPolicyId,
      // max_price
      maxPriceLovelace,
      // protocol_fee
      fees.protocolFee,
    ];

    // Mint listing NFT (empty token name)
    if (LISTING_NFT_POLICY_SCRIPT_CBOR) {
      txBuilder
        .mintPlutusScriptV3()
        .mint("1", LISTING_NFT_POLICY_ID, "")
        .mintingScript(LISTING_NFT_POLICY_SCRIPT_CBOR)
        .mintRedeemerValue({ alternative: 0, fields: [] }); // any redeemer, mint validator ignores it
    }

    // Output to escrow: locked lovelace + listing NFT with inline datum
    txBuilder.txOut(ESCROW_SCRIPT_ADDRESS, [
      { unit: "lovelace", quantity: lockedAmount.toString() },
      ...(LISTING_NFT_POLICY_ID
        ? [{ unit: LISTING_NFT_POLICY_ID, quantity: "1" }]
        : []),
    ]);

    txBuilder.txOutInlineDatumValue({
      constructor: 0,
      fields: [
        { bytes: ownerPkh },
        stakePkh
          ? {
              constructor: 0,
              fields: [
                { constructor: 0, fields: [{ bytes: ownerPkh }] },
                {
                  constructor: 0,
                  fields: [
                    {
                      constructor: 0,
                      fields: [
                        { constructor: 0, fields: [{ bytes: stakePkh }] },
                      ],
                    },
                  ],
                },
              ],
            }
          : {
              constructor: 1,
              fields: [
                { constructor: 0, fields: [{ bytes: ownerPkh }] },
              ],
            },
        { bytes: targetPolicyId },
        { int: maxPriceLovelace },
        { int: fees.protocolFee },
      ],
    });
  }

  txBuilder.changeAddress(changeAddress);
  txBuilder.selectUtxosFrom(utxos);

  const unsignedTx = await txBuilder.complete();
  const signedTx = await wallet.signTx(unsignedTx);
  const txHash = await wallet.submitTx(signedTx);

  return { txHash };
}

export interface CancelSnipeParams {
  wallet: WalletLike;
  snipeUtxos: Array<{
    txHash: string;
    outputIndex: number;
  }>;
}

/**
 * Build and submit a "Cancel Snipe" transaction.
 *
 * For each snipe UTxO:
 * 1. Spend the escrow UTxO with Cancel redeemer
 * 2. Burn the listing NFT (-1)
 * ADA returns to wallet change address.
 */
export async function cancelSnipeTx(
  params: CancelSnipeParams
): Promise<CreateSnipeResult> {
  const { wallet, snipeUtxos } = params;

  const { MeshTxBuilder } = await import("@meshsdk/core");

  const usedAddresses = await wallet.getUsedAddresses();
  if (!usedAddresses.length) throw new Error("No wallet address found");
  const changeAddress = usedAddresses[0];

  const utxos = await wallet.getUtxos();

  const txBuilder = new MeshTxBuilder();

  // Reference input: settings UTxO
  if (SETTINGS_UTXO_TX_HASH) {
    txBuilder.readOnlyTxInReference(
      SETTINGS_UTXO_TX_HASH,
      SETTINGS_UTXO_OUTPUT_INDEX
    );
  }

  for (const snipeUtxo of snipeUtxos) {
    // Spend escrow UTxO with Cancel redeemer (Constr 1)
    txBuilder
      .spendingPlutusScriptV3()
      .txIn(snipeUtxo.txHash, snipeUtxo.outputIndex)
      .spendingReferenceTxInInlineDatumPresent()
      .spendingReferenceTxInRedeemerValue({ alternative: 1, fields: [] }); // Cancel = Constr(1)

    if (LISTING_NFT_POLICY_SCRIPT_CBOR) {
      txBuilder.txInScript(LISTING_NFT_POLICY_SCRIPT_CBOR);
    }

    // Burn listing NFT
    if (LISTING_NFT_POLICY_SCRIPT_CBOR) {
      txBuilder
        .mintPlutusScriptV3()
        .mint("-1", LISTING_NFT_POLICY_ID, "")
        .mintingScript(LISTING_NFT_POLICY_SCRIPT_CBOR)
        .mintRedeemerValue({ alternative: 1, fields: [] }); // Burn redeemer
    }
  }

  // Required signer (owner PKH)
  const { resolvePaymentKeyHash } = await import("@meshsdk/core");
  txBuilder.requiredSignerHash(resolvePaymentKeyHash(changeAddress));

  txBuilder.changeAddress(changeAddress);
  txBuilder.selectUtxosFrom(utxos);

  const unsignedTx = await txBuilder.complete();
  const signedTx = await wallet.signTx(unsignedTx);
  const txHash = await wallet.submitTx(signedTx);

  return { txHash };
}
