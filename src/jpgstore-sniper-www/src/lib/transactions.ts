import { MeshTxBuilder, deserializeAddress } from "@meshsdk/core";
import { mConStr0, mConStr1 } from "@meshsdk/common";
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

  const fees: FeeBreakdown = calculateFees(settings, maxPriceLovelace);
  const lockedAmount =
    maxPriceLovelace +
    fees.operatorFee +
    fees.protocolFee +
    fees.txFeeBudget;

  console.log("[createSnipeTx] settings:", settings);
  console.log("[createSnipeTx] contracts:", contracts);
  console.log("[createSnipeTx] fees:", fees);
  console.log("[createSnipeTx] lockedAmount:", lockedAmount);
  console.log("[createSnipeTx] targetPolicyId:", targetPolicyId);

  const usedAddresses = await wallet.getUsedAddresses();
  if (!usedAddresses.length) throw new Error("No wallet address found");
  const changeAddress = usedAddresses[0];

  const { pubKeyHash: ownerPkh, stakeCredentialHash: stakePkh } =
    deserializeAddress(changeAddress);

  console.log("[createSnipeTx] ownerPkh:", ownerPkh, "stakePkh:", stakePkh);

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
      .mintRedeemerValue(mConStr0([]));

    // Build inline datum for PolicySnipeDatum
    // Aiken Address = Constr 0 [payment_credential, Some(stake_credential)]
    //               | Constr 1 [payment_credential] (no stake)
    // payment_credential = Constr 0 [Constr 0 [pkh]]  (VerificationKeyCredential)
    // stake_credential   = Constr 0 [Constr 0 [Constr 0 [stake_pkh]]]
    const paymentCred = mConStr0([mConStr0([ownerPkh])]);
    const nftDestination = stakePkh
      ? mConStr0([
          paymentCred,
          mConStr0([mConStr0([mConStr0([stakePkh])])]),
        ])
      : mConStr1([paymentCred]);

    // PolicySnipeDatum { owner_pkh, nft_destination, policy_id, max_price, protocol_fee }
    const datum = mConStr0([
      ownerPkh,
      nftDestination,
      targetPolicyId,
      maxPriceLovelace,
      fees.protocolFee,
    ]);

    console.log("[createSnipeTx] datum:", JSON.stringify(datum));

    // Output to escrow: locked lovelace + listing NFT with inline datum
    txBuilder.txOut(contracts.escrowScriptAddress, [
      { unit: "lovelace", quantity: lockedAmount.toString() },
      { unit: contracts.listingNftPolicyId, quantity: "1" },
    ]);
    txBuilder.txOutInlineDatumValue(datum);
  }

  txBuilder.changeAddress(changeAddress);
  txBuilder.selectUtxosFrom(utxos);

  await txBuilder.complete();
  const unsignedTx = txBuilder.txHex;
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

  const usedAddresses = await wallet.getUsedAddresses();
  if (!usedAddresses.length) throw new Error("No wallet address found");
  const changeAddress = usedAddresses[0];

  const { pubKeyHash: ownerPkh } = deserializeAddress(changeAddress);

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
      .spendingReferenceTxInRedeemerValue(mConStr1([]));
    txBuilder.txInScript(contracts.listingNftPolicyCbor);

    // Burn listing NFT
    txBuilder
      .mintPlutusScriptV3()
      .mint("-1", contracts.listingNftPolicyId, "")
      .mintingScript(contracts.listingNftPolicyCbor)
      .mintRedeemerValue(mConStr1([]));
  }

  // Required signer (owner PKH)
  txBuilder.requiredSignerHash(ownerPkh);

  txBuilder.changeAddress(changeAddress);
  txBuilder.selectUtxosFrom(utxos);

  await txBuilder.complete();
  const unsignedTx = txBuilder.txHex;
  const signedTx = await wallet.signTx(unsignedTx);
  const txHash = await wallet.submitTx(signedTx);

  return { txHash };
}
