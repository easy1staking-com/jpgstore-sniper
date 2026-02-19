package com.easy1staking.jpgstore.sniper.service;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.easy1staking.cardano.model.AssetType;
import com.easy1staking.jpgstore.sniper.model.contract.v2.ListingDetails;
import com.easy1staking.jpgstore.sniper.model.contract.v2.PaymentDetails;
import com.easy1staking.util.Pair;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ListingDatumParser {

    private final ObjectMapper objectMapper;

    /**
     * like `findPlutusData` BUT the listingMetadata is the resolved CBOR
     *
     * @param listingMetadata the Hex encoded CBOR of the (list of) Datum
     * @param datumHash       the datumHash of the datum to find
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
                    } catch (Exception e) {
                        return Stream.empty();
                    }
                })
                .findFirst();
    }

    /**
     * Whether is a Datum in the tx Metadata or an inline Datum this methods transform the
     * Hex encoded CBOR datum into a json string representation of the Plutus Data
     *
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
                        } catch (Exception e) {
                            log.error("error while deserialising datum", e);
                            return Optional.empty();
                        }
                    });
        } catch (Exception e) {
            log.error("error while deserialising datum", e);
            return Optional.empty();

        }
    }

    /**
     * Like the other method but from a PlutusData object
     *
     * @param plutusData
     * @return
     */
    public Optional<String> deserializeDatum(PlutusData plutusData) {
        try {
            return Optional.of(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(plutusData));
        } catch (Exception e) {
            log.error("error while deserialising datum", e);
            return Optional.empty();
        }
    }

    /**
     * Given a Json String representation of the PlutusData of a Datum, extracts the ListingDetails
     *
     * @param datum
     * @return
     */
    public Optional<ListingDetails> extractListingDetailsV1(String datum) {
        try {
            JsonNode jsonNode = JsonUtil.parseJson(datum);

            var fields = jsonNode.path("fields");
            var ownerPkh = fields.get(0).path("bytes").asText();
            var paymentDetailsList = fields.get(1).path("list");

            var payees = new ArrayList<PaymentDetails>();
            paymentDetailsList.forEach(node -> {
                Address address = extractAddress(node.path("fields")
                        .get(0)
                        .path("fields"));
                var assets = extractAssets(node.path("fields").get(1).path("map"));
                payees.add(new PaymentDetails(address.getAddress(), assets));
            });

            return Optional.of(new ListingDetails(ownerPkh, payees));

        } catch (Exception e) {
            log.error("asd", e);
        }

        return Optional.empty();
    }

    public Optional<ListingDetails> extractListingDetailsV2(String datum) {
        log.info("datum: {}", datum);
        try {
            JsonNode jsonNode = JsonUtil.parseJson(datum);

            var fields = jsonNode.path("fields");

            var paymentDetails = fields.get(0).path("list");

            var payees = new ArrayList<PaymentDetails>();
            paymentDetails.forEach(node -> payees.add(extractPaymentDetails(node)));

            var ownerPkh = fields.get(1).path("bytes").asText();

            return Optional.of(new ListingDetails(ownerPkh, payees));

        } catch (Exception e) {
            log.warn("error", e);
            return Optional.empty();
        }

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


    private List<Pair<AssetType, Long>> extractAssets(JsonNode jsonNode) {
        var assetAmounts = new ArrayList<Pair<AssetType, Long>>();
        jsonNode.forEach(node -> {
            var policyId = node.path("k").path("bytes").asText();
            node.path("v").path("fields").get(1).path("map").forEach(amountNode -> {
                var assetName = amountNode.path("k").path("bytes").asText();
                var amount = amountNode.path("v").path("int").asLong();
                assetAmounts.add(new Pair<>(AssetType.fromUnit(policyId + assetName), amount));
            });
        });
        return assetAmounts;
    }

    private PaymentDetails extractPaymentDetails(JsonNode node) {
        var addressRoot = node.path("fields").get(0);
        var paymentPkh = addressRoot.path("fields").get(0).path("fields").get(0).path("bytes").asText();
        var stakingPkh = addressRoot.path("fields").get(1).path("fields").get(0).path("fields").get(0).path("fields").get(0).path("bytes").asText();
        log.info("paymentPkh: {}, stakingPkh: {}", paymentPkh, stakingPkh);
        var address = AddressProvider.getBaseAddress(Credential.fromKey(paymentPkh), Credential.fromKey(stakingPkh), Networks.mainnet());
        var lovelaces = node.path("fields").get(1).path("int").asLong();
        return new PaymentDetails(address.getAddress(), List.of(new Pair<>(AssetType.ada(), lovelaces)));
    }

    public Optional<ListingDetails> parseDatumV1(String inlineDatum) {
        try {
            PlutusData oracleUpdate = PlutusData.deserialize(HexUtil.decodeHexString(inlineDatum));
            var json = objectMapper.writeValueAsString(oracleUpdate);
            return this.extractListingDetailsV1(json);
        } catch (Exception e) {
            log.warn("error", e);
            return Optional.empty();
        }
    }

    public Optional<ListingDetails> parseDatumV2(String inlineDatum) {
        try {
            PlutusData datum = PlutusData.deserialize(HexUtil.decodeHexString(inlineDatum));
            var json = objectMapper.writeValueAsString(datum);
            return this.extractListingDetailsV2(json);
        } catch (Exception e) {
            log.warn("error", e);
            return Optional.empty();
        }
    }

    /**
     * From the listing transaction attached metadata, fetches the listing details for the given datumHash.
     * Supports multiple listing
     * @param metadataCborHex the transaction's metadata in cborHex format
     * @param datumHash the datum hash for which extracting the ListingDetails.
     * @return ListingDetails if parsing succeeds
     */
    public Optional<ListingDetails> parsePaymentDetailsV1(String metadataCborHex, String datumHash) {

        var metadataMap = CBORMetadata.deserialize(HexUtil.decodeHexString(metadataCborHex));

        String listingMetadata = metadataMap
                .getData()
                .getValues()
                .stream()
                .map(Object::toString)
                .toList()
                .stream()
                .skip(1)
                .collect(Collectors.joining());

        return extractPlutusData(listingMetadata, datumHash)
                .flatMap(this::deserializeDatum)
                .flatMap(this::extractListingDetailsV1);

    }


    /**
     * From the listing transaction attached metadata, fetches the listing details for the given datumHash.
     * Supports multiple listing
     * @param metadataCborHex the transaction's metadata in cborHex format
     * @param datumHash the datum hash for which extracting the ListingDetails.
     * @return ListingDetails if parsing succeeds
     */
    public Optional<ListingDetails> parsePaymentDetailsV2(String metadataCborHex, String datumHash) {

        var metadataMap = CBORMetadata.deserialize(HexUtil.decodeHexString(metadataCborHex));

        String listingMetadata = metadataMap
                .getData()
                .getValues()
                .stream()
                .map(Object::toString)
                .toList()
                .stream()
                .skip(1)
                .collect(Collectors.joining());

        return extractPlutusData(listingMetadata, datumHash)
                .flatMap(this::deserializeDatum)
                .flatMap(this::extractListingDetailsV2);

    }

}
