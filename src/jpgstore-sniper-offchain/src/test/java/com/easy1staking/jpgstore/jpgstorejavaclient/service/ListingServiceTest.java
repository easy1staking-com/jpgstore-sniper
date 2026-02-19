package com.easy1staking.jpgstore.jpgstorejavaclient.service;

import com.easy1staking.jpgstore.sniper.config.BlockfrostConfig;
import com.easy1staking.jpgstore.sniper.service.ListingService;
import io.blockfrost.sdk.api.model.AddressUtxo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {BlockfrostConfig.class, ListingService.class})
class ListingServiceTest {

    @Autowired
    ListingService listingService;

    @Test
    void listingIsFound() {
        Optional<AddressUtxo> listingOpt = listingService.findListing("a5bb0e5bb275a573d744a021f9b3bff73595468e002755b447e01559484f534b594361736847726162303030333838303130");

        assertTrue(listingOpt.isPresent());

        listingOpt.ifPresent(utxo -> {
            // Hash of the datum to use in the Plutus purchase transaction to select and unlock the utxo where the NFT is
            assertEquals("9d71518ca19a2f20ee6cae5a55fb96ea8562b0dd872cad40a59cde705e849983",  utxo.getDataHash());

            // Utxo where the NFT is sitting, but also the listing transaction id, required to find metadata
            assertEquals("d7148618def46151338ab0ddfef64e0a5fb4f8075e53e29410465a168a347bd6",  utxo.getTxHash());

            // utxo index, required to select nft in purchase tx
            assertEquals(7,  utxo.getOutputIndex());
        });

    }
    @Test
    void cheekyListing() {
        Optional<AddressUtxo> listingOpt = listingService.findListing("a5bb0e5bb275a573d744a021f9b3bff73595468e002755b447e01559484f534b594361736847726162303030333037363131");

        assertTrue(listingOpt.isPresent());

        listingOpt.ifPresent(utxo -> {
            System.out.println(utxo);
        });

    }

}