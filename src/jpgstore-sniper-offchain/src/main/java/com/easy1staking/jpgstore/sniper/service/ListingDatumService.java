package com.easy1staking.jpgstore.sniper.service;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.easy1staking.jpgstore.sniper.model.ListingDetails;
import com.easy1staking.jpgstore.sniper.model.PaymentDetails;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockfrost.sdk.api.TransactionService;
import io.blockfrost.sdk.api.exception.APIException;
import io.blockfrost.sdk.api.model.TransactionMetadataJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ListingDatumService {

    private final TransactionService transactionService;

    private final ObjectMapper objectMapper;

    /**
     * NFT can be listed one or more at a time. Metadata are used to store the Datum
     * required to purchase the NFT
     * This method allows to extract the correct datum from a listing tx hash and the expected
     * datum hash stored in the utxo of the NFT listed in the contract and that we want to purchase
     * @param txHash NFT listing tx hash
     * @param datumHash the datumHash of the datum to find
     * @return
     */
    public Optional<PlutusData> findPlutusData(String txHash, String datumHash) {
        List<TransactionMetadataJson> transactionMetadata = null;
        try {
            transactionMetadata = transactionService.getTransactionMetadata(txHash);
            String metadata = transactionMetadata
                    .stream()
                    // Stripping unnecessary metadata entry
                    .filter(txMetadataJson -> !txMetadataJson.getLabel().equals("30"))
                    .map(txMetadataJson -> txMetadataJson.getJsonMetadata().asText())
                    .collect(Collectors.joining());

            return extractPlutusData(metadata, datumHash);

        } catch (APIException e) {
            log.error("unable to extract listing datum", e);
            return Optional.empty();
        }

    }

    /**
     * like `findPlutusData` BUT the listingMetadata is the resolved CBOR
     * @param listingMetadata the Hex encoded CBOR of the (list of) Datum
     * @param datumHash the datumHash of the datum to find
     * @return
     */
    public Optional<PlutusData> extractPlutusData(String listingMetadata, String datumHash) {
        List<String> listingMeta = List.of(listingMetadata.split(","));

        return listingMeta
                .stream()
                .flatMap(metadataItem -> {
                    try {
                        Optional<DataItem> metadataCbor = CborDecoder
                                .decode(HexUtil.decodeHexString(metadataItem))
                                .stream()
                                .findFirst();
                        if (metadataCbor.isPresent()) {
                            ConstrPlutusData plutusData = ConstrPlutusData.deserialize(metadataCbor.get());
                            if (plutusData.getDatumHash().equalsIgnoreCase(datumHash)) {
                                return Stream.<PlutusData>of(plutusData);
                            } else {
                                return Stream.empty();
                            }
                        } else {
                            return Stream.empty();
                        }
                    } catch (CborDeserializationException | CborException e) {
                        return Stream.empty();
                    }
                })
                .findFirst();
    }

    /**
     * Whether is a Datum in the tx Metadata or an inline Datum this methods transform the
     * Hex encoded CBOR datum into a json string representation of the Plutus Data
     * @param datum
     * @return
     */
    public Optional<String> deserializeDatum(String datum) {
        try {
            List<DataItem> decode = CborDecoder.decode(HexUtil.decodeHexString(datum));
            return decode
                    .stream()
                    .findFirst()
                    .flatMap(dataItem -> {
                        try {
                            ConstrPlutusData data = ConstrPlutusData.deserialize(dataItem);
                            return Optional.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data));
                        } catch (CborDeserializationException | JsonProcessingException e) {
                            log.error("error while deserialising datum", e);
                            return Optional.empty();
                        }
                    });
        } catch (CborException e) {
            log.error("error while deserialising datum", e);
            return Optional.empty();

        }
    }

    /**
     * Like the other method but from a PlutusData object
     * @param plutusData
     * @return
     */
    public Optional<String> deserializeDatum(PlutusData plutusData) {
        try {
            return Optional.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(plutusData));
        } catch (JsonProcessingException e) {
            log.error("error while deserialising datum", e);
            return Optional.empty();
        }
    }

    /**
     * Given a Json String representation of the PlutusData of a Datum, extracts the ListingDetails
     * @param datum
     * @return
     */
    public Optional<ListingDetails> extractListingDetails(String datum) {

        try {
            JsonNode jsonNode = JsonUtil.parseJson(datum);

            JsonNode paymentDetailsList = jsonNode
                    .path("fields")
                    .get(1)
                    .path("list");

            JsonNode jpgstorePaymentDetails = paymentDetailsList.get(0);
            Address jpgstoreFeeAddress = extractAddress(jpgstorePaymentDetails.path("fields")
                    .get(0)
                    .path("fields"));
            Long jpgStoreFee = extractLovelace(jpgstorePaymentDetails);

            JsonNode royaltiesPaymentDetails = paymentDetailsList.get(1);
            Address royaltiesFeeAddress = extractAddress(royaltiesPaymentDetails.path("fields")
                    .get(0)
                    .path("fields"));
            Long royalties = extractLovelace(royaltiesPaymentDetails);

            JsonNode sellerPaymentDetails = paymentDetailsList.get(2);
            Address selleFeeAddress = extractAddress(sellerPaymentDetails.path("fields")
                    .get(0)
                    .path("fields"));
            Long nftPrice = extractLovelace(sellerPaymentDetails);

            return Optional.of(new ListingDetails(
                    new PaymentDetails(jpgstoreFeeAddress.getAddress(), jpgStoreFee),
                    Optional.of(new PaymentDetails(royaltiesFeeAddress.getAddress(), royalties)),
                    new PaymentDetails(selleFeeAddress.getAddress(), nftPrice)
            ));

        } catch (JsonProcessingException e) {
            log.error("asd", e);
        }

        return Optional.empty();
    }

    private Address extractAddress(JsonNode node) {
        String jpgPkh = node
                .get(0)
                .path("fields")
                .get(0)
                .path("bytes")
                .asText();
        String jpgPsh = node
                .get(1)
                .path("fields")
                .get(0)
                .path("fields")
                .get(0)
                .path("fields")
                .get(0)
                .path("bytes")
                .asText();
        Credential pkh = Credential.fromKey(jpgPkh);
        Credential stkCred = Credential.fromKey(jpgPsh);
        return AddressProvider.getBaseAddress(pkh, stkCred, Networks.mainnet());
    }

    private Long extractLovelace(JsonNode node) {
        return node.path("fields")
                .get(1)
                .path("map")
                .get(0)
                .path("v")
                .path("fields")
                .get(1)
                .path("map")
                .get(0)
                .path("v")
                .path("int")
                .asLong();
    }

}
