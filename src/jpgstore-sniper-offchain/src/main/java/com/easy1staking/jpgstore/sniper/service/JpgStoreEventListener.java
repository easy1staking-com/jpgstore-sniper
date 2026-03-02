package com.easy1staking.jpgstore.sniper.service;

import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.ValueUtil;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.store.events.TransactionEvent;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.model.UtxoId;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.repository.UtxoRepository;
import com.easy1staking.cardano.model.AssetType;
import com.easy1staking.cardano.util.UtxoUtil;
import com.easy1staking.jpgstore.sniper.contract.PolicyIdSnipeContract;
import com.easy1staking.jpgstore.sniper.contract.SettingsContract;
import com.easy1staking.jpgstore.sniper.model.contract.v2.PaymentDetails;
import com.easy1staking.jpgstore.sniper.model.onchain.SettingsParser;
import com.easy1staking.jpgstore.sniper.model.onchain.SnipeDatumParser;
import com.easy1staking.math.Rational;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.Collection;

import static com.easy1staking.jpgstore.sniper.model.Constants.JPG_CONTRACT_ADDRESS_V2;

@Service
@RequiredArgsConstructor
@Slf4j
public class JpgStoreEventListener {

    private final ObjectMapper objectMapper;

    private final Network network;

    private final Account account;

    private final SettingsContract settingsContract;

    private final PolicyIdSnipeContract policyIdSnipeContract;

    private final SettingsParser settingsParser;

    private final ListingDatumParser listingDatumParser;

    private final ListingDatumService listingDatumService;

    private final SnipeDatumParser snipeDatumParser;

    private final SnipeRegistry snipeRegistry;

    private final NftCollectionService nftCollectionService;

    private final NftTokenService nftTokenService;

    private final BFBackendService bfBackendService;

    private final UtxoRepository utxoRepository;

    private final HybridUtxoSupplier hybridUtxoSupplier;

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

                            var metadataCbor = transaction.getAuxData().getMetadataCbor();

                            var listingDatumOpt = listingDatumParser.resolveListingDatum(metadataCbor, output.getDatumHash());
                            if (listingDatumOpt.isEmpty()) {
                                log.warn("could not process payment details for utxo: {}", output);
                                return;
                            }

                            log.info("output.getDatumHash(): {}", output.getDatumHash());

                            var listingDatum = listingDatumOpt.get();
                            log.info("listingDatum: {}", listingDatum.serializeToHex());
                            log.info("listingDatum hash: {}", listingDatum.getDatumHash());


                            var listingDatum2Opt = listingDatumService.findPlutusData(transaction.getTxHash(), output.getDatumHash());
                            if (listingDatum2Opt.isEmpty()) {
                                return;
                            }

                            var listingDatum2 = listingDatum2Opt.get();
                            log.info("listingDatum2: {}", listingDatum2.serializeToHex());
                            log.info("listingDatum2 hash: {}", listingDatum2.getDatumHash());


                            var listingDetailsOpt = listingDatumParser.parsePaymentDetailsV2(metadataCbor, output.getDatumHash());

                            if (listingDetailsOpt.isEmpty()) {
                                log.warn("could not process payment details for utxo: {}", output);
                                return;
                            }

                            var listingDetails = listingDetailsOpt.get();

                            output.getAmounts()
                                    .stream()
                                    .filter(amount -> amount.getQuantity().equals(BigInteger.ONE))
                                    .forEach(nft -> {
                                        var assetType = AssetType.fromUnit(nft.getUnit());
                                        log.info("processing: {}", assetType);

//                                        var collection = nftCollectionService.getCollection(assetType.policyId());
//                                        collection.ifPresent(nftCollection -> log.info("collection: {}", nftCollection.getDisplayName()));

//                                        var token = nftTokenService.getToken(assetType.toUnit());
//                                        token.ifPresent(nftToken -> log.info("token: {}", nftToken.getDisplayName()));

                                        var policySnipes = snipeRegistry.getPolicySnipe(assetType.policyId());
                                        log.info("found {} policy snipes", policySnipes.size());

                                        if (!policySnipes.isEmpty()) {

                                            var snipe = policySnipes.getFirst();
                                            log.info("about to process snipe: {}", snipe);

                                            // NFT Utxo
                                            var jpgNftUtxo = UtxoUtil.toUtxo(output, transaction.getTxHash(), j);
                                            log.info("jpgNftUtxo: {}", jpgNftUtxo);
                                            hybridUtxoSupplier.add(jpgNftUtxo);

                                            Utxo jpgNftUtxo2;
                                            try {
                                                jpgNftUtxo2 = bfBackendService.getUtxoService().getTxOutput(transaction.getTxHash(), j).getValue();
                                                log.info("jpgNftUtxo2: {}", jpgNftUtxo2);
                                            } catch (ApiException e) {
                                                throw new RuntimeException(e);
                                            }

                                            var snipeUtxoOpt = utxoRepository.findById(UtxoId.builder()
                                                    .txHash(snipe.getTransactionId())
                                                    .outputIndex(snipe.getIndex())
                                                    .build());

                                            if (snipeUtxoOpt.isEmpty()) {
                                                log.warn("could not find jpgNftUtxo: {}", jpgNftUtxo);
                                                return;
                                            }

                                            // Snipe UTXO
                                            var snipeUtxo = snipeUtxoOpt.map(UtxoUtil::toUtxo).get();
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

                                            var totalAmount = listingDetails.payees().stream().map(PaymentDetails::amount).reduce(Value::add).orElse(Value.builder().build()).getCoin();
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


                                            var snipeAddressPkh = new Address(snipeUtxo.getAddress()).getPaymentCredentialHash().map(HexUtil::encodeHexString).get();
                                            log.info("snipeAddressPkh: {}", snipeAddressPkh);
                                            log.info("policyIdSnipeContract: {}", policyIdSnipeContract.getScriptHash());

                                            var snipeTx = new ScriptTx()
                                                    // Collect SNIPE
                                                    .collectFrom(snipeUtxo, ConstrPlutusData.of(0))
                                                    // Collect NFT
                                                    .collectFrom(jpgNftUtxo2, ConstrPlutusData.of(0, BigIntPlutusData.of(0)), listingDatum2)
                                                    // BURN Snipe NFT
                                                    .mintAsset(policyIdSnipeContract.getPlutusScript(), Asset.builder().value(BigInteger.ONE.negate()).build(), ConstrPlutusData.of(0))
                                                    // Pay JPG Store Fees
                                                    .payToContract("addr1xxzvcf02fs5e282qk3pmjkau2emtcsj5wrukxak3np90n2evjel5h55fgjcxgchp830r7h2l5msrlpt8262r3nvr8eksg6pw3p", Amount.lovelace(actualJpgStoreFees), txFeeDatum)
                                                    .attachSpendingValidator(policyIdSnipeContract.getPlutusScript());

                                            // JPG Payees
                                            listingDetails.payees()
                                                    .forEach(paymentDetails -> {
                                                        log.info("beneficiary: {}", paymentDetails.beneficiary());
                                                        snipeTx.payToAddress(paymentDetails.beneficiary(), ValueUtil.toAmountList(paymentDetails.amount()));
                                                    });

                                            // Deliver NFT
                                            snipeTx.
                                                    payToContract(nftDestination.getAddress(), Amount.asset(assetType.toUnit(), BigInteger.ONE), snipeTag)
                                                    // Pay Sniper Protocol
                                                    .payToContract(treasuryAddress.getAddress(), Amount.ada(1L), snipeTag)
                                                    .readFrom(TransactionInput.builder()
                                                            .transactionId(settingsUtxo.getTxHash())
                                                            .index(settingsUtxo.getOutputIndex())
                                                            .build())
                                                    // JPG Contract
                                                    .readFrom("1693c508b6132e89b932754d657d28b24068ff5ff1715fec36c010d4d6470b3d", 0)
                                                    .withChangeAddress(account.baseAddress());

                                            var snipeTransaction = new QuickTxBuilder(bfBackendService).compose(snipeTx)
                                                    .feePayer(account.baseAddress())
                                                    .mergeOutputs(false)
                                                    .withSigner(SignerProviders.signerFrom(account))
//                                                    .withTxEvaluator(new AikenTransactionEvaluator(bfBackendService))
                                                    .preBalanceTx((txBuilderContext, transaction1) -> {
                                                        try {
                                                            log.info("pre tx: {}", objectMapper.writeValueAsString(transaction1));
                                                        } catch (JsonProcessingException e) {
                                                            log.warn("error", e);
                                                        }
                                                    })
                                                    .buildAndSign();

//                                            if (!snipeTransaction.isSuccessful()) {
//                                                log.error("error: {}", snipeTransaction.getResponse());
//                                            }

                                            hybridUtxoSupplier.clear();

                                        }

                                        var merkleSnipes = snipeRegistry.getMerkleSnipe(assetType.policyId());
                                        log.info("found {} merkle snipes", merkleSnipes.size());

                                    });

                        }
                    }

                });


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
