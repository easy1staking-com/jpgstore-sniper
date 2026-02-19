package com.easy1staking.jpgstore.sniper.service;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.store.events.TransactionEvent;
import com.easy1staking.cardano.model.AssetType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

import static com.easy1staking.jpgstore.sniper.model.Constants.JPG_CONTRACT_ADDRESS_V2;

@Service
@RequiredArgsConstructor
@Slf4j
public class JpgStoreEventListener {

    private String jpgStoreContractHashV2;

    private final ListingDatumParser listingDatumParser;

    private final NftCollectionService nftCollectionService;

    private final NftTokenService nftTokenService;

    @PostConstruct
    public void init() {
        log.info("INIT");
        jpgStoreContractHashV2 = new Address(JPG_CONTRACT_ADDRESS_V2).getPaymentCredentialHash().map(HexUtil::encodeHexString).get();
    }

    @EventListener
    public void processEvent(TransactionEvent transactionEvent) {

        transactionEvent.getTransactions()
                .forEach(transaction -> {
                    transaction.getBody()
                            .getOutputs()
                            .stream()
                            .filter(addressUtxo -> JPG_CONTRACT_ADDRESS_V2.equals(addressUtxo.getAddress()))
                            .forEach(address -> {

                                address.getAmounts()
                                        .stream()
                                        .filter(foo -> foo.getQuantity().equals(BigInteger.ONE))
                                        .forEach(nft -> {
                                            var assetType = AssetType.fromUnit(nft.getUnit());
                                            var collection = nftCollectionService.getCollection(assetType.policyId());
                                            log.info("collection: {}", collection);
                                            var token = nftTokenService.getToken(assetType.toUnit());
                                            log.info("token: {}", token);
                                        });


                            });
                });


    }


}
