package com.easy1staking.jpgstore.sniper.service;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.util.HexUtil;
import com.easy1staking.cardano.model.AssetType;
import com.easy1staking.jpgstore.sniper.model.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockfrost.sdk.api.AddressService;
import io.blockfrost.sdk.api.TransactionService;
import io.blockfrost.sdk.api.exception.APIException;
import io.blockfrost.sdk.api.model.AddressUtxo;
import io.blockfrost.sdk.api.util.OrderEnum;
import io.blockfrost.sdk.impl.TransactionServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.bloxbean.cardano.client.backend.blockfrost.common.Constants.BLOCKFROST_MAINNET_URL;
import static com.easy1staking.jpgstore.sniper.util.PlutusDataUtil.plutusDataValue;

@Service
@RequiredArgsConstructor
@Slf4j
public class ListingService {

    private final ObjectMapper objectMapper;

    private final Account account;

    private final AddressService blockfrostAddressService;

    /**
     * jpg.store seems to be using a combination of PlutusV1, PlutusV2 with Reference Scripts,
     * but does not make use of inline datum but only data hash in PlutusV1 style.
     * <p>
     * The Plutus Data or Datum that needs to be used with the purchase script, seems to have been attached at NFT listing time.
     * In order to buy an NFT, given its asset_id aka unit (see below for definition), it is necessary to find
     * listing transaction, fetch the utxo (i.e. listing transaction), find the transaction and extract Plutus Data / Datum
     * from the transaction Metadata.
     * <p>
     * As a result of reverse engineer the purchase action, it's necessary to find the listing transaction of the
     * NFT to purchase.
     *
     * @param unit unit or asset id, hex encode of policy id + asset name
     * @return
     */
    public Optional<AddressUtxo> findListing(String unit) {
        try {
            return blockfrostAddressService
                    .getAddressUtxosGivenAsset(Constants.JPG_CONTRACT_ADDRESS_V1, unit, 1, 1, OrderEnum.asc)
                    .stream()
                    .findFirst();
        } catch (APIException e) {
            log.error("error while fetching listing details", e);
            return Optional.empty();
        }
    }

    public void listAsset(AssetType assetType) {
        this.listAsset(assetType, false);
    }

    public void listAsset(AssetType assetType, boolean dryRun) {

        BFBackendService bfBackendService = new BFBackendService(BLOCKFROST_MAINNET_URL, "mainnetKWaNkQcrF1erC3u3SZjaFxZiM2M20jFM");

        ConstrPlutusData plutusData = ConstrPlutusData.of(0,
                BytesPlutusData.of(HexUtil.decodeHexString("9860e4b40ea7c06903fc1a96f4448e1a616ed06f96ee62bf2e689d67")),
                ListPlutusData.of(
                        ConstrPlutusData.of(0,
                                ConstrPlutusData.of(0,
                                        ConstrPlutusData.of(0, BytesPlutusData.of(HexUtil.decodeHexString("9860e4b40ea7c06903fc1a96f4448e1a616ed06f96ee62bf2e689d67"))),
                                        ConstrPlutusData.of(0,
                                                ConstrPlutusData.of(0,
                                                        ConstrPlutusData.of(0, BytesPlutusData.of(HexUtil.decodeHexString("acead18addb6e4f2ab14ddf9a2b442c3675e855639ba1083bad6089a")))))
                                ),
                                plutusDataValue(1_000_000L)
                        ),
                        ConstrPlutusData.of(0,
                                ConstrPlutusData.of(0,
                                        ConstrPlutusData.of(0, BytesPlutusData.of(HexUtil.decodeHexString("9860e4b40ea7c06903fc1a96f4448e1a616ed06f96ee62bf2e689d67"))),
                                        ConstrPlutusData.of(0,
                                                ConstrPlutusData.of(0,
                                                        ConstrPlutusData.of(0, BytesPlutusData.of(HexUtil.decodeHexString("acead18addb6e4f2ab14ddf9a2b442c3675e855639ba1083bad6089a")))))
                                ),
                                plutusDataValue(1_000_000L)
                        ),
                        ConstrPlutusData.of(0,
                                ConstrPlutusData.of(0,
                                        ConstrPlutusData.of(0, BytesPlutusData.of(HexUtil.decodeHexString("9860e4b40ea7c06903fc1a96f4448e1a616ed06f96ee62bf2e689d67"))),
                                        ConstrPlutusData.of(0,
                                                ConstrPlutusData.of(0,
                                                        ConstrPlutusData.of(0, BytesPlutusData.of(HexUtil.decodeHexString("acead18addb6e4f2ab14ddf9a2b442c3675e855639ba1083bad6089a")))))
                                ),
                                plutusDataValue(200_000_000L)
                        )
                )
        );

        String datumHexEncoded = plutusData.serializeToHex();
        List<String> datumParts = new ArrayList<>();
        while (datumHexEncoded.length() > 64) {
            String substring = datumHexEncoded.substring(0, 64);
            datumParts.add(substring);
            datumHexEncoded = datumHexEncoded.substring(64);
        }
        datumParts.add(datumHexEncoded + ",");

        Metadata metadata = MetadataBuilder.createMetadata();
        metadata
                .put(30, "5");

        for (int i = 0; i < datumParts.size(); i++) {
            metadata.put(50 + i, datumParts.get(i));
        }

        Tx tx = new Tx()
                .payToContract(Constants.JPG_CONTRACT_ADDRESS_V1,
                        Amount.asset(assetType.policyId(), "0x" + assetType.assetName(), 1),
                        plutusData.getDatumHash()
                )
                .from(account.baseAddress())
                .attachMetadata(metadata);

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(bfBackendService);
        var transaction = quickTxBuilder
                .compose(tx)
                .withSigner(SignerProviders.signerFrom(account))
                .feePayer(account.baseAddress())
                .build();

        try {
            log.info("tx: {}", objectMapper.writeValueAsString(transaction));
            if (!dryRun) {
                bfBackendService.getTransactionService().submitTransaction(transaction.serialize());
            }
        } catch (Exception e) {
            log.warn("error", e);
        }


    }

}
