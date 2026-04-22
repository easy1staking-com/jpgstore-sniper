package com.easy1staking.jpgstore.sniper.service;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.ValueUtil;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.store.events.TransactionEvent;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.model.UtxoId;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.repository.UtxoRepository;
import com.easy1staking.cardano.model.AssetType;
import com.easy1staking.cardano.util.UtxoUtil;
import com.easy1staking.jpgstore.sniper.contract.MerkleTreeSnipeContract;
import com.easy1staking.jpgstore.sniper.contract.PolicyIdSnipeContract;
import com.easy1staking.jpgstore.sniper.contract.SettingsContract;
import com.easy1staking.jpgstore.sniper.model.SnipeType;
import com.easy1staking.jpgstore.sniper.model.contract.v2.ListingDetails;
import com.easy1staking.jpgstore.sniper.model.entity.SnipeId;
import com.easy1staking.jpgstore.sniper.model.onchain.SettingsParser;
import com.easy1staking.jpgstore.sniper.model.onchain.SnipeDatumParser;
import com.easy1staking.jpgstore.sniper.repository.MerkleSnipeRepository;
import com.easy1staking.math.Rational;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cardanofoundation.merkle.MerkleTree;
import org.cardanofoundation.merkle.ProofItem;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import static com.easy1staking.jpgstore.sniper.model.Constants.*;
import static com.easy1staking.jpgstore.sniper.model.SnipeType.MERKLE;
import static com.easy1staking.jpgstore.sniper.model.SnipeType.POLICY;

@Service
@RequiredArgsConstructor
@Slf4j
public class JpgStoreEventListener {

    private final Network network;

    private final Account account;

    private final SettingsContract settingsContract;

    private final PolicyIdSnipeContract policyIdSnipeContract;

    private final MerkleTreeSnipeContract merkleTreeSnipeContract;

    private final SettingsParser settingsParser;

    private final ListingDatumParser listingDatumParser;

    private final SnipeDatumParser snipeDatumParser;

    private final SnipeRegistry snipeRegistry;

    private final BFBackendService bfBackendService;

    private final UtxoRepository utxoRepository;

    private final MerkleSnipeRepository merkleSnipeRepository;

    private final QuickTxBuilder quickTxBuilder;

    @PostConstruct
    public void init() {
        log.info("INIT");
    }

    @EventListener
    public void processEvent(TransactionEvent transactionEvent) {

        transactionEvent.getTransactions()
                .forEach(transaction -> {

                    var outputs = transaction.getBody().getOutputs();

                    for (int i = 0; i < outputs.size(); i++) {
                        final var j = i;
                        var output = outputs.get(i);

                        if (JPG_CONTRACT_ADDRESS_V2.equals(output.getAddress())) {

                            var auxData = transaction.getAuxData();
                            if (auxData == null) {
                                log.info("auxData is null");
                                return;
                            }

                            var metadataCbor = auxData.getMetadataCbor();
                            if (metadataCbor == null) {
                                log.info("metadataCbor is null");
                                return;
                            }

                            var listingDatumOpt = listingDatumParser.resolveListingDatum(metadataCbor, output.getDatumHash());
                            if (listingDatumOpt.isEmpty()) {
                                log.warn("could not process payment details for utxo: {}", output);
                                return;
                            }

                            var listingDatum = listingDatumOpt.get();

                            var listingDetailsOpt = listingDatumParser.parsePaymentDetailsV2(metadataCbor, output.getDatumHash());

                            if (listingDetailsOpt.isEmpty()) {
                                log.warn("could not process payment details for utxo: {}", output);
                                return;
                            }

                            var listingDetails = listingDetailsOpt.get();

                            output.getAmounts()
                                    .stream()
                                    .filter(amount -> amount.getQuantity().equals(BigInteger.ONE))
                                    .forEach(amount -> {
                                        var nft = AssetType.fromUnit(amount.getUnit());

                                        var policySnipes = snipeRegistry.getPolicySnipe(nft.policyId());

                                        if (!policySnipes.isEmpty()) {
                                            processSnipe(policySnipes.getFirst(),
                                                    POLICY,
                                                    nft,
                                                    UtxoUtil.toUtxo(output, transaction.getTxHash(), j),
                                                    listingDatum,
                                                    listingDetails);
                                        }

                                        var merkleSnipes = snipeRegistry.getMerkleSnipe(nft.toUnit());

                                        if (!merkleSnipes.isEmpty()) {
                                            processSnipe(merkleSnipes.getFirst(),
                                                    MERKLE,
                                                    nft,
                                                    UtxoUtil.toUtxo(output, transaction.getTxHash(), j),
                                                    listingDatum,
                                                    listingDetails);
                                        }

                                    });

                        }
                    }

                });


    }

    public void processSnipe(TransactionInput snipe,
                             SnipeType snipeType,
                             AssetType nft,
                             Utxo jpgNftUtxo,
                             PlutusData listingDatum,
                             ListingDetails listingDetails) {
        log.info("about to process snipe: {}", snipe);
        log.info("jpgNftUtxo: {}", jpgNftUtxo);
        try {

            var snipeUtxoOpt = utxoRepository.findById(UtxoId.builder()
                            .txHash(snipe.getTransactionId())
                            .outputIndex(snipe.getIndex())
                            .build())
                    .map(UtxoUtil::toUtxo);

            if (snipeUtxoOpt.isEmpty()) {
                log.warn("could not find jpgNftUtxo: {}", jpgNftUtxo);
                return;
            }

            // Snipe UTXO
            var snipeUtxo = snipeUtxoOpt.get();
            log.info("snipeUtxo: {}", snipeUtxo);

            var snipeDatumOpt = snipeDatumParser.parse(snipeUtxo.getInlineDatum());
            if (snipeDatumOpt.isEmpty()) {
                log.warn("could not parse jpgNftUtxo: {}", snipeUtxo);
                return;
            }

            var snipeDatum = snipeDatumOpt.get();

            var nftDestinationAddress = snipeDatum.nftDestination();

            Address nftDestination;
            if (nftDestinationAddress.stakeKeyHash() != null) {
                nftDestination = AddressProvider.getBaseAddress(nftDestinationAddress.paymentKeyHash(), nftDestinationAddress.stakeKeyHash(), network);
            } else {
                nftDestination = AddressProvider.getEntAddress(nftDestinationAddress.paymentKeyHash(), network);
            }

            var totalAmount = listingDetails.totalAmount().getCoin();
            var expectedJpgStoreNftFees = Rational.from(totalAmount).multiply(Rational.from(20L, 980L)).floor();
            log.info("expectedJpgStoreNftFees: {}", expectedJpgStoreNftFees);

            var actualJpgStoreFees = BigInteger.valueOf(1_000_000L).max(expectedJpgStoreNftFees);
            log.info("actualJpgStoreFees: {}", actualJpgStoreFees);

            var nftCost = totalAmount.add(actualJpgStoreFees);

            if (snipeDatum.maxPrice() >= nftCost.longValue()) {
                log.info("ENOUGH");
            } else {
                log.info("NFT PRICE TOO HIGH - max price: {} -> nft cost: {}", snipeDatum.maxPrice(), nftCost);
                return;
            }

            var txFeeDatum = getJpgUtxoHash(jpgNftUtxo.getTxHash(), jpgNftUtxo.getOutputIndex());

            var snipeTag = getUtxoHash(snipeUtxo.getTxHash(), snipeUtxo.getOutputIndex());
            log.info("snipeTag: {}", snipeTag.serializeToHex());

            var settingsUtxoOpt = utxoRepository.findUnspentByOwnerPaymentCredential(settingsContract.getScriptHash(), Pageable.unpaged())
                    .stream()
                    .flatMap(Collection::stream)
                    .map(UtxoUtil::toUtxo)
                    .findAny();

            if (settingsUtxoOpt.isEmpty()) {
                log.warn("could not find utxo by settings: {}", settingsContract.getScriptHash());
                return;
            }

            var settingsUtxo = settingsUtxoOpt.get();

            var settingsOpt = settingsParser.parse(settingsUtxo.getInlineDatum());
            if (settingsOpt.isEmpty()) {
                log.warn("could not parse utxo: {}", settingsUtxo);
                return;
            }

            var settings = settingsOpt.get();
            var protocolTreasuryAddress = settings.protocolTreasury();

            var treasuryAddress = AddressProvider.getBaseAddress(protocolTreasuryAddress.paymentKeyHash(), protocolTreasuryAddress.stakeKeyHash(), network);

            var snipeRedeemerOpt = resolveSnipeRedeemer(snipe, snipeType, nft);

            if (snipeRedeemerOpt.isEmpty()) {
                log.warn("could not compute proof");
                return;
            }

            var snipeRedeemer = snipeRedeemerOpt.get();

            var snipeContract = switch (snipeType) {
                case POLICY -> policyIdSnipeContract.getPlutusScript();
                case MERKLE -> merkleTreeSnipeContract.getPlutusScript();
            };

            var snipeTx = new Tx()
                    // Collect SNIPE
                    .collectFrom(snipeUtxo, snipeRedeemer)
                    // Collect NFT
                    .collectFrom(jpgNftUtxo, ConstrPlutusData.of(0, BigIntPlutusData.of(0)), listingDatum)
                    // BURN Snipe NFT
                    .mintAsset(snipeContract, Asset.builder().value(BigInteger.ONE.negate()).build(), ConstrPlutusData.of(1))
                    // Pay JPG Store Fees
                    .payToContract(JPG_STORE_FEE_ADDRESS, Amount.lovelace(actualJpgStoreFees), txFeeDatum);

            // JPG Payees
            listingDetails.payees()
                    .forEach(paymentDetails -> {
                        log.info("beneficiary: {}", paymentDetails.beneficiary());
                        snipeTx.payToAddress(paymentDetails.beneficiary(), ValueUtil.toAmountList(paymentDetails.amount()));
                    });

            // Deliver NFT
            snipeTx.
                    payToContract(nftDestination.getAddress(), Amount.asset(nft.toUnit(), BigInteger.ONE), snipeTag)
                    // Pay Sniper Protocol
                    .payToContract(treasuryAddress.getAddress(), Amount.lovelace(BigInteger.valueOf(snipeDatum.protocolFee())), snipeTag)
                    .readFrom(settingsUtxo)
                    // JPG Contract
                    .readFrom(JPG_STORE_V2_CONTRACT_REF_INPUT)
                    .withChangeAddress(account.baseAddress());

            var snipeTransaction = quickTxBuilder.compose(snipeTx)
                    .feePayer(account.baseAddress())
                    .mergeOutputs(false)
                    .withSigner(SignerProviders.signerFrom(account))
                    .buildAndSign();


            bfBackendService.getTransactionService()
                    .submitTransaction(snipeTransaction.serialize());
        } catch (Exception e) {
            log.warn("error", e);
        }

    }

    private Optional<ConstrPlutusData> resolveSnipeRedeemer(TransactionInput snipe, SnipeType snipeType, AssetType nft) {
        return switch (snipeType) {
            case POLICY -> Optional.of(ConstrPlutusData.of(0));
            case MERKLE -> merkleSnipeRepository.findById(SnipeId.builder()
                            .txHash(snipe.getTransactionId())
                            .outputIndex(snipe.getIndex())
                            .build())
                    .flatMap(merkleSnipe -> {

                        var nfts = Arrays.asList(merkleSnipe.getNftList().split(","));

                        var merkleTree = MerkleTree.fromList(nfts, HexUtil::decodeHexString);

                        var proofOpt = MerkleTree.getProof(merkleTree, nft.toUnit(), HexUtil::decodeHexString);
                        if (proofOpt.isEmpty()) {
                            log.warn("could not compute proof");
                            return Optional.empty();
                        }

                        var proof = proofOpt.get();
                        log.info("proof: {}", proof);

                        var proofList = proof.toJavaList()
                                .stream()
                                .map(proofItem -> switch (proofItem) {
                                    case ProofItem.Left left ->
                                            ConstrPlutusData.of(0, ConstrPlutusData.of(0, BytesPlutusData.of(left.getHash())));
                                    case ProofItem.Right right ->
                                            ConstrPlutusData.of(1, ConstrPlutusData.of(0, BytesPlutusData.of(right.getHash())));
                                    default -> throw new IllegalArgumentException();
                                })
                                .toList();

                        return Optional.of(ConstrPlutusData.of(0,
                                BytesPlutusData.of(HexUtil.decodeHexString(nft.policyId())),
                                BytesPlutusData.of(HexUtil.decodeHexString(nft.assetName())),
                                ListPlutusData.of(proofList.toArray(new PlutusData[]{}))
                        ));
                    });

        };

    }


    public PlutusData getJpgUtxoHash(String txHash, int outputIndex) {
        var outRefHash = ConstrPlutusData.of(0,
                ConstrPlutusData.of(0, BytesPlutusData.of(HexUtil.decodeHexString(txHash))),
                BigIntPlutusData.of(outputIndex)
        );
        return BytesPlutusData.of(outRefHash.getDatumHashAsBytes());
    }

    public PlutusData getUtxoHash(String txHash, int outputIndex) {
        var outRefHash = ConstrPlutusData.of(0,
                BytesPlutusData.of(HexUtil.decodeHexString(txHash)),
                BigIntPlutusData.of(outputIndex)
        );
        return BytesPlutusData.of(outRefHash.getDatumHashAsBytes());
    }

}
