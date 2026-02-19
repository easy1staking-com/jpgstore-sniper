package com.easy1staking.jpgstore.jpgstorejavaclient.service;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.easy1staking.jpgstore.sniper.config.AccountConfig;
import com.easy1staking.jpgstore.sniper.config.BlockfrostConfig;
import com.easy1staking.jpgstore.sniper.model.ListingDetails;
import com.easy1staking.jpgstore.sniper.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {AccountConfig.class, BlockfrostConfig.class,
        JpgstorePurchaseService.class, ListingDatumService.class, HybridUtxoSupplier.class, JpgstoreScriptProvider.class,
        LocalTransactionProcessor.class, StaticProtocolParamsSupplier.class})
class JpgstorePurchaseServiceTest {

    @Autowired
    private JpgstorePurchaseService jpgstorePurchaseService;

    @Autowired
    private BFBackendService bfBackendService;

    @Autowired
    private ListingDatumService listingDatumService;


    @Test
    public void purchaseNft() throws ApiException {

        Result<List<Utxo>> utxos = bfBackendService.getUtxoService().getUtxos("addr1zxgx3far7qygq0k6epa0zcvcvrevmn0ypsnfsue94nsn3tvpw288a4x0xf8pxgcntelxmyclq83s0ykeehchz2wtspks905plm",
                "edc3b9defc34b59c16785276bf4bf4a7785f6e30474ac7e5ccb316f043726f7374414441",
                1,
                1);

        utxos.getValue().stream().findFirst()
                .ifPresent(utxo -> {
                    System.out.println(utxo);

                    Optional<PlutusData> plutusData = listingDatumService.findPlutusData(utxo.getTxHash(), utxo.getDataHash());

                    Optional<ListingDetails> listingDetails = plutusData
                            .flatMap(data -> listingDatumService.deserializeDatum(data))
                            .flatMap(datum -> listingDatumService.extractListingDetails(datum));

                    try {
                        jpgstorePurchaseService.purchaseNft(utxo, plutusData.get(), listingDetails.get());
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                });


        assertTrue(true);

    }

}