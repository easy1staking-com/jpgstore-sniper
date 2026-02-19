package com.easy1staking.jpgstore.sniper.service;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.ValueUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.transaction.spec.AuxiliaryData;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.transaction.util.TransactionUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.helper.LocalClientProvider;
import com.easy1staking.jpgstore.sniper.model.ListingDetails;
import com.easy1staking.jpgstore.sniper.model.SniperNFT;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.Vector;
import java.util.stream.Collectors;

import static com.easy1staking.jpgstore.sniper.model.Constants.JPG_CONTRACT_ADDRESS_V1;

@Slf4j
public class JpgstoreSniperService {

    @Autowired
    private LocalClientProvider localClientProvider;

    @Autowired
    private ListingDatumService listingDatumService;

    @Autowired
    private JpgstorePurchaseService jpgstorePurchaseService;

    @Autowired
    private HybridUtxoSupplier hybridUtxoSupplier;

    @PostConstruct
    public void startSniper() {

        List<String> txAlreadySeen = new Vector<>();

        localClientProvider.getTxMonitorClient()
                .streamMempoolTransactions()
                .map(bytes -> {
                    Optional<Transaction> transactionOpt;
                    try {
                        transactionOpt = Optional.of(Transaction.deserialize(bytes));
                    } catch (CborDeserializationException e) {
                        transactionOpt = Optional.empty();
                    }
                    return transactionOpt;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(tx -> {
                    if (txAlreadySeen.size() % 1000 == 0) {
                        txAlreadySeen.clear();
                    }
                    String txHash = TransactionUtil.getTxHash(tx);
                    if (txAlreadySeen.contains(txHash)) {
                        return false;
                    } else {
                        txAlreadySeen.add(txHash);
                        return true;
                    }
                })
                .filter(tx -> tx.getBody()
                        .getOutputs()
                        .stream()
                        .anyMatch(txOutput -> txOutput.getAddress().equalsIgnoreCase(JPG_CONTRACT_ADDRESS_V1)))
                .subscribe(tx -> {

                    hybridUtxoSupplier.extractMempoolUtxo(tx);

                    String txHash = TransactionUtil.getTxHash(tx);

                    List<TransactionOutput> txsOutput = tx.getBody()
                            .getOutputs()
                            .stream()
                            .filter(txOutput -> txOutput.getAddress().equalsIgnoreCase(JPG_CONTRACT_ADDRESS_V1))
                            .toList();

                    for (int i = 0; i < txsOutput.size(); i++) {
                        final int j = i;
                        TransactionOutput txOutput = txsOutput.get(i);
                        Value value = txOutput.getValue();

                        value.getMultiAssets()
                                .stream()
//                                .filter(multiAsset -> policiesOfInterest.contains(multiAsset.getPolicyId()))
                                .forEach(multiAsset -> multiAsset.getAssets().forEach(asset -> {
                                                    AuxiliaryData auxiliaryData = tx.getAuxiliaryData();
                                                    if (auxiliaryData != null) {
                                                        Metadata metadata = auxiliaryData.getMetadata();
                                                        if (metadata != null) {
                                                            String listingMetadata = metadata
                                                                    .getData()
                                                                    .getValues()
                                                                    .stream()
                                                                    .map(Object::toString)
                                                                    .toList()
                                                                    .stream()
                                                                    .skip(1)
                                                                    .collect(Collectors.joining());
                                                            Optional<PlutusData> plutusDataOpt = listingDatumService.extractPlutusData(listingMetadata, HexUtil.encodeHexString(txOutput.getDatumHash()));

                                                            plutusDataOpt
                                                                    .flatMap(listingDatumService::deserializeDatum)
                                                                    .flatMap(listingDatumService::extractListingDetails)
                                                                    .filter(details -> {
                                                                        String assetName = new String(asset.getNameAsBytes());
                                                                        log.info("Policy: {}, Name: {}, Price (ada) {}", multiAsset.getPolicyId(), assetName, details.totalPrice() / 1_000_000);
                                                                        Optional<SniperNFT> match = Optional.empty();
//                                                                                sniperNFTS.stream()
//                                                                                .filter(sniperNFT -> sniperNFT.policyId().equalsIgnoreCase(multiAsset.getPolicyId()) &&
//                                                                                        sniperNFT.assetName().equalsIgnoreCase(assetName)).findFirst();
                                                                        if (match.isPresent()) {
                                                                            long maxPriceLovelaces = match.get().maxPrice() * 1_000_000;
                                                                            if (maxPriceLovelaces >= details.totalPrice()) {
                                                                                log.info("About to purchse: {}", details);
                                                                                return true;
                                                                            } else {
                                                                                log.info("Too expensive: {}, max price: {}", details, match.get().maxPrice());
                                                                                return false;
                                                                            }

                                                                        } else {
                                                                            log.info("Not a match");
                                                                            return false;
                                                                        }
                                                                    })
                                                                    .ifPresent(details -> {
                                                                        Utxo utxo = Utxo.builder()
                                                                                .txHash(txHash)
                                                                                .outputIndex(j)
                                                                                .address(txOutput.getAddress())
                                                                                .dataHash(HexUtil.encodeHexString(txOutput.getDatumHash()))
                                                                                .amount(ValueUtil.toAmountList(txOutput.getValue()))
                                                                                .build();

                                                                        Optional<ListingDetails> listingDetails = listingDatumService.deserializeDatum(plutusDataOpt.get())
                                                                                .flatMap(listingDatumService::extractListingDetails);

                                                                        try {
                                                                            jpgstorePurchaseService.purchaseNft(utxo, plutusDataOpt.get(), listingDetails.get());
                                                                        } catch (URISyntaxException e) {
                                                                            throw new RuntimeException(e);
                                                                        }
                                                                    });


                                                        } else {
                                                            System.out.println("No metadata attached, this should not happen");
                                                        }
                                                    } else {
                                                        System.out.println("No auxilieary attached, this should not happen");
                                                    }
                                                }
                                        )
                                );


                    }


                });

    }


}
