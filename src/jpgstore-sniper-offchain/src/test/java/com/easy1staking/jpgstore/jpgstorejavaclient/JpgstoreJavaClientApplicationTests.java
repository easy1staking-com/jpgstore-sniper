package com.easy1staking.jpgstore.jpgstorejavaclient;

import com.easy1staking.jpgstore.sniper.config.BlockfrostConfig;
import com.easy1staking.jpgstore.sniper.service.ListingDatumService;
import io.blockfrost.sdk.api.AddressService;
import io.blockfrost.sdk.api.MetadataService;
import io.blockfrost.sdk.api.TransactionService;
import io.blockfrost.sdk.api.exception.APIException;
import io.blockfrost.sdk.api.model.AddressUtxo;
import io.blockfrost.sdk.api.model.Transaction;
import io.blockfrost.sdk.api.model.TransactionMetadataJson;
import io.blockfrost.sdk.api.util.OrderEnum;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest(classes = {BlockfrostConfig.class, ListingDatumService.class})
class JpgstoreJavaClientApplicationTests {

    @Autowired
    private AddressService addressService;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private MetadataService metadataService;
    @Autowired
    private ListingDatumService processJpgMetadataListing;


    @Test
    void contextLoads() throws APIException {

//        AddressUtxo(txHash=d7148618def46151338ab0ddfef64e0a5fb4f8075e53e29410465a168a347bd6, outputIndex=7, amount=[TransactionOutputAmount(unit=lovelace, quantity=1361960), TransactionOutputAmount(unit=a5bb0e5bb275a573d744a021f9b3bff73595468e002755b447e01559484f534b594361736847726162303030333838303130, quantity=1)], block=fa921fe0b284e2d92a65623a6f2bba5d57004f619e4cb6740e3177235d782dc8, dataHash=9d71518ca19a2f20ee6cae5a55fb96ea8562b0dd872cad40a59cde705e849983, inlineDatum=null, referenceScriptHash=null)

//        String policyId = "a5bb0e5bb275a573d744a021f9b3bff73595468e002755b447e01559";
        List<AddressUtxo> addressUtxosGivenAsset = addressService
                .getAddressUtxosGivenAsset("addr1zxgx3far7qygq0k6epa0zcvcvrevmn0ypsnfsue94nsn3tvpw288a4x0xf8pxgcntelxmyclq83s0ykeehchz2wtspks905plm",
                        "a5bb0e5bb275a573d744a021f9b3bff73595468e002755b447e01559484f534b594361736847726162303030333838303130",
                        1, 1, OrderEnum.asc);

        addressUtxosGivenAsset
                .stream()
                .findFirst()
                .ifPresent(addressUtxo -> {

                    String dataHash = addressUtxo.getDataHash();
                    System.out.println("required data hash: " + dataHash);

                    String txHash = addressUtxo.getTxHash();
                    System.out.println("tx hash: " + txHash);

                    Integer outputIndex = addressUtxo.getOutputIndex();
                    System.out.println("output index: " + outputIndex);

                    try {
                        Transaction transaction = transactionService.getTransaction(txHash);
                        List<TransactionMetadataJson> transactionMetadata = transactionService.getTransactionMetadata(txHash);
                        transactionMetadata
                                .forEach(System.out::println);

                        String metadata = transactionMetadata
                                .stream()
                                .filter(txMetadataJson -> !txMetadataJson.getLabel().equals("30"))
                                .map(foo -> foo.getJsonMetadata().asText())
                                .collect(Collectors.joining());

                        System.out.println(metadata);

                        List<String> listingMeta = List.of(metadata.split(","));

//                        listingMeta
//                                .forEach(processJpgMetadataListing::parseMetadata);


                    } catch (APIException e) {
                        throw new RuntimeException(e);
                    }

                });

    }

}
