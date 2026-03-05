import { ContractInfo, Settings, FeeBreakdown } from "./types";
import { calculateFees } from "./fees";

// MeshJS wallet shape from useWallet()
interface WalletLike {
  getUsedAddresses(): Promise<string[]>;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  getUtxos(): Promise<any[]>;
  signTx(unsignedTx: string, partialSign?: boolean): Promise<string>;
  submitTx(signedTx: string): Promise<string>;
}

export interface CreateSnipeParams {
  wallet: WalletLike;
  contracts: ContractInfo;
  settings: Settings;
  targetPolicyId: string;
  maxPriceLovelace: number;
  quantity: number;
}

export interface TxResult {
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
): Promise<TxResult> {
  const {
    wallet,
    contracts,
    settings,
    targetPolicyId,
    maxPriceLovelace,
    quantity,
  } = params;

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
  txBuilder.readOnlyTxInReference(
    contracts.settingsUtxoTxHash,
    contracts.settingsUtxoOutputIndex
  );

  for (let i = 0; i < quantity; i++) {
    // Mint listing NFT (empty token name)
    txBuilder
      .mintPlutusScriptV3()
      .mint("1", contracts.listingNftPolicyId, "")
      .mintingScript(contracts.listingNftPolicyCbor)
      .mintRedeemerValue({ alternative: 0, fields: [] });

    // Build inline datum for PolicySnipeDatum
    const nftDestination = stakePkh
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
          fields: [{ constructor: 0, fields: [{ bytes: ownerPkh }] }],
        };

    const datum = {
      constructor: 0,
      fields: [
        { bytes: ownerPkh },
        nftDestination,
        { bytes: targetPolicyId },
        { int: maxPriceLovelace },
        { int: fees.protocolFee },
      ],
    };

    // Output to escrow: locked lovelace + listing NFT with inline datum
    txBuilder.txOut(contracts.escrowScriptAddress, [
      { unit: "lovelace", quantity: lockedAmount.toString() },
      { unit: contracts.listingNftPolicyId, quantity: "1" },
    ]);
    txBuilder.txOutInlineDatumValue(datum);
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
  contracts: ContractInfo;
  snipeUtxos: Array<{
    txHash: string;
    outputIndex: number;
  }>;
}

/**
 * Build and submit a "Cancel Snipe" transaction.
 *
 * For each snipe UTxO:
 * 1. Spend the escrow UTxO with Cancel redeemer (Constr 1)
 * 2. Burn the listing NFT (-1)
 * ADA returns to wallet change address.
 */
export async function cancelSnipeTx(
  params: CancelSnipeParams
): Promise<TxResult> {
  const { wallet, contracts, snipeUtxos } = params;

  const { MeshTxBuilder, resolvePaymentKeyHash } =
    await import("@meshsdk/core");

  const usedAddresses = await wallet.getUsedAddresses();
  if (!usedAddresses.length) throw new Error("No wallet address found");
  const changeAddress = usedAddresses[0];

  const utxos = await wallet.getUtxos();
  const txBuilder = new MeshTxBuilder();

  // Reference input: settings UTxO
  txBuilder.readOnlyTxInReference(
    contracts.settingsUtxoTxHash,
    contracts.settingsUtxoOutputIndex
  );

  for (const snipeUtxo of snipeUtxos) {
    // Spend escrow UTxO with Cancel redeemer (Constr 1)
    txBuilder
      .spendingPlutusScriptV3()
      .txIn(snipeUtxo.txHash, snipeUtxo.outputIndex)
      .spendingReferenceTxInInlineDatumPresent()
      .spendingReferenceTxInRedeemerValue({ alternative: 1, fields: [] });
    txBuilder.txInScript(contracts.listingNftPolicyCbor);

    // Burn listing NFT
    txBuilder
      .mintPlutusScriptV3()
      .mint("-1", contracts.listingNftPolicyId, "")
      .mintingScript(contracts.listingNftPolicyCbor)
      .mintRedeemerValue({ alternative: 1, fields: [] });
  }

  // Required signer (owner PKH)
  txBuilder.requiredSignerHash(resolvePaymentKeyHash(changeAddress));

  txBuilder.changeAddress(changeAddress);
  txBuilder.selectUtxosFrom(utxos);

  const unsignedTx = await txBuilder.complete();
  const signedTx = await wallet.signTx(unsignedTx);
  const txHash = await wallet.submitTx(signedTx);

  return { txHash };
}
