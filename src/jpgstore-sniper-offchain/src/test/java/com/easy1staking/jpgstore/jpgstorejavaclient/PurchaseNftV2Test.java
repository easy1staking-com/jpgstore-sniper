package com.easy1staking.jpgstore.jpgstorejavaclient;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.ValueUtil;
import com.bloxbean.cardano.client.backend.api.DefaultProtocolParamsSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultScriptSupplier;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.util.HexUtil;
import com.easy1staking.cardano.model.AssetType;
import com.easy1staking.jpgstore.sniper.model.contract.v2.PaymentDetails;
import com.easy1staking.jpgstore.sniper.service.ListingDatumParser;
import com.easy1staking.jpgstore.sniper.service.ListingDatumService;
import com.easy1staking.math.Rational;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.blockfrost.sdk.api.AddressService;
import io.blockfrost.sdk.api.TransactionService;
import io.blockfrost.sdk.api.util.OrderEnum;
import io.blockfrost.sdk.impl.AddressServiceImpl;
import io.blockfrost.sdk.impl.TransactionServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import scalus.bloxbean.ScalusTransactionEvaluator;
import scalus.bloxbean.ScriptSupplier;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.easy1staking.jpgstore.sniper.model.Constants.JPG_CONTRACT_ADDRESS_V2;

@Slf4j
public class PurchaseNftV2Test {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String MNEMONIC = System.getenv("WALLET_MNEMONIC");

    private static final String BLOCKFROST_KEY = System.getenv("BLOCKFROST_KEY");

    private final BFBackendService bfBackendService = new BFBackendService(Constants.BLOCKFROST_MAINNET_URL, BLOCKFROST_KEY);

    private final AddressService addressService = new AddressServiceImpl(Constants.BLOCKFROST_MAINNET_URL, BLOCKFROST_KEY);

    private final TransactionService transactionService = new TransactionServiceImpl(Constants.BLOCKFROST_MAINNET_URL, BLOCKFROST_KEY);

    private final ListingDatumParser listingDatumParser = new ListingDatumParser(OBJECT_MAPPER);

    private final ListingDatumService listingDatumService = new ListingDatumService(transactionService, OBJECT_MAPPER);

    @Test
    public void purchaseV2() throws Exception {

        var account = Account.createFromMnemonic(Networks.mainnet(), MNEMONIC, 2, 0);

        log.info("BLOCKFROST_KEY: {}", BLOCKFROST_KEY);

        var assetType = AssetType.fromUnit("4523c5e21d409b81c95b45b0aea275b8ea1406e6cafea5583b9f8a5f000de14042756436303233");

        var addressUtxo = addressService
                .getAddressUtxosGivenAsset(JPG_CONTRACT_ADDRESS_V2, assetType.toUnit(), 1, 1, OrderEnum.asc)
                .getFirst();
        log.info("addressUtxo: {}", addressUtxo);

        var outRefHash = ConstrPlutusData.of(0,
                ConstrPlutusData.of(0, BytesPlutusData.of(HexUtil.decodeHexString("8b5af333d5ee6166ef168aff8ef6310050bf8dd378995124e018d39534fa845b"))),
                BigIntPlutusData.of(addressUtxo.getOutputIndex())
        );
        // d8799fd8799f58208b5af333d5ee6166ef168aff8ef6310050bf8dd378995124e018d39534fa845bff00ff
        // D8799FD8799F58208B5AF333D5EE6166EF168AFF8EF6310050BF8DD378995124E018D39534FA845BFF00FF
        log.info("outRefHash: {}", outRefHash.serializeToHex());
        log.info("outRefHash: {}", outRefHash.getDatumHash());
        log.info("outRefHash: {}", HexUtil.encodeHexString(Blake2bUtil.blake2bHash256(outRefHash.serializeToBytes())));

        var listingDatumOpt = listingDatumService.findPlutusData(addressUtxo.getTxHash(), addressUtxo.getDataHash());
        if (listingDatumOpt.isEmpty()) {
            Assertions.fail();
        }

        var listingDatum = listingDatumOpt.get();
        log.info("listingDatum: {}", listingDatum.serializeToHex());

        var listingDetailsOpt = listingDatumParser.parseDatumV2(listingDatum.serializeToHex());

        if (listingDetailsOpt.isEmpty()) {
            Assertions.fail();
        }

        var listingDetails = listingDetailsOpt.get();
        log.info("listingDetails: {}", listingDetails);

        listingDetails.payees().forEach(payee -> log.info("payee: {}", payee));

        var utxo = bfBackendService.getUtxoService().getTxOutput(addressUtxo.getTxHash(), addressUtxo.getOutputIndex()).getValue();

        var walletUtxos = bfBackendService.getUtxoService().getUtxos(account.baseAddress(), 100, 1).getValue();

        var datumHash = listingDatum.getDatumHash();
        log.info("datumHash: {}", datumHash);

        var bar = listingDetails.payees().stream().map(PaymentDetails::amount).reduce(Value::add).orElse(Value.builder().build()).getCoin();
//        var jpgStoreFees = Rational.from(bar).multiply(Rational.from(2L, 98L)).floor();
        var jpgStoreFees = Rational.from(bar).multiply(Rational.from(25L, 975L)).floor();
        log.info("jpgStoreFees: {}", jpgStoreFees);


        var tx = new ScriptTx()
                .collectFrom(walletUtxos)
                .collectFrom(utxo, ConstrPlutusData.of(0, BigIntPlutusData.of(0)), listingDatum)
                .payToContract("addr1xxzvcf02fs5e282qk3pmjkau2emtcsj5wrukxak3np90n2evjel5h55fgjcxgchp830r7h2l5msrlpt8262r3nvr8eksg6pw3p", Amount.lovelace(jpgStoreFees), BytesPlutusData.of(HexUtil.decodeHexString("4873d29746dc9d57e3dc3ee30207b6f90e953f999206ceb823c5e004ff2802b3")));

        // payees
        listingDetails.payees().forEach(payee -> tx.payToAddress(payee.beneficiary(), ValueUtil.toAmountList(payee.amount())));

        tx.readFrom("1693c508b6132e89b932754d657d28b24068ff5ff1715fec36c010d4d6470b3d", 0)
                .withChangeAddress(account.baseAddress());

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(bfBackendService);

        var transaction = quickTxBuilder.compose(tx)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .preBalanceTx((txBuilderContext, transaction1) -> {
                    try {
                        log.info("pre tx: {}", OBJECT_MAPPER.writeValueAsString(transaction1));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .build();

        log.info("tx: {}", OBJECT_MAPPER.writeValueAsString(transaction));

    }

    @Test
    public void purchaseV2Test2() throws Exception {

        var account = Account.createFromMnemonic(Networks.mainnet(), MNEMONIC, 2, 0);

        log.info("BLOCKFROST_KEY: {}", BLOCKFROST_KEY);

        var assetType = AssetType.fromUnit("4523c5e21d409b81c95b45b0aea275b8ea1406e6cafea5583b9f8a5f000de14042756436303233");

        var addressUtxo = addressService
                .getAddressUtxosGivenAsset(JPG_CONTRACT_ADDRESS_V2, assetType.toUnit(), 1, 1, OrderEnum.asc)
                .getFirst();
        log.info("addressUtxo: {}", addressUtxo);

        var listingDatumOpt = listingDatumService.findPlutusData(addressUtxo.getTxHash(), addressUtxo.getDataHash());
        if (listingDatumOpt.isEmpty()) {
            Assertions.fail();
        }

        var listingDatum = listingDatumOpt.get();
        log.info("listingDatum: {}", listingDatum.serializeToHex());

        var listingDetailsOpt = listingDatumParser.parseDatumV2(listingDatum.serializeToHex());

        if (listingDetailsOpt.isEmpty()) {
            Assertions.fail();
        }

        var listingDetails = listingDetailsOpt.get();
        log.info("listingDetails: {}", listingDetails);

        var utxo = bfBackendService.getUtxoService().getTxOutput(addressUtxo.getTxHash(), addressUtxo.getOutputIndex()).getValue();

        var walletUtxos = bfBackendService.getUtxoService().getUtxos(account.baseAddress(), 100, 1).getValue();

        //        jpg fees 7.98
//        difference = amount × (2/98)

        var bar = listingDetails.payees().stream().map(PaymentDetails::amount).reduce(Value::add).orElse(Value.builder().build()).getCoin();
        var jpgStoreFees = Rational.from(bar).multiply(Rational.from(2L, 98L)).floor();
        log.info("jpgStoreFees: {}", jpgStoreFees);

        final var tx = new ScriptTx()
                .collectFrom(walletUtxos)
                .collectFrom(utxo, ConstrPlutusData.of(0, BigIntPlutusData.of(0)), listingDatum)

                // fees
                .payToContract("addr1xxzvcf02fs5e282qk3pmjkau2emtcsj5wrukxak3np90n2evjel5h55fgjcxgchp830r7h2l5msrlpt8262r3nvr8eksg6pw3p", Amount.lovelace(jpgStoreFees), BytesPlutusData.of(HexUtil.decodeHexString("53cc96ff7c850f6b2a44c59ad463956251b684fc913cf7829c1e928dc822ab56")));

        // payees
        listingDetails.payees().forEach(payee -> tx.payToAddress(payee.beneficiary(), ValueUtil.toAmountList(payee.amount())));

        tx.readFrom("1693c508b6132e89b932754d657d28b24068ff5ff1715fec36c010d4d6470b3d", 0)
                .withChangeAddress(account.baseAddress());

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(bfBackendService);

        var transaction = quickTxBuilder.compose(tx)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .preBalanceTx((txBuilderContext, transaction1) -> {
                    try {
                        log.info("pre tx: {}", OBJECT_MAPPER.writeValueAsString(transaction1));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .build();

        log.info("tx: {}", OBJECT_MAPPER.writeValueAsString(transaction));

    }

    @Test
    public void purchaseV2Test_ExtraContract() throws Exception {

        var alwaysTrueContract = "585c01010029800aba2aba1aab9eaab9dab9a4888896600264653001300600198031803800cc0180092225980099b8748008c01cdd500144c8cc892898050009805180580098041baa0028b200c180300098019baa0068a4d13656400401";

        var alwaysTrueScript = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(alwaysTrueContract, PlutusVersion.v3);

        var alwaysTrueAddress = AddressProvider.getEntAddress(alwaysTrueScript, Networks.mainnet());

        var account = Account.createFromMnemonic(Networks.mainnet(), MNEMONIC, 2, 0);

        log.info("BLOCKFROST_KEY: {}", BLOCKFROST_KEY);

        var assetType = AssetType.fromUnit("4523c5e21d409b81c95b45b0aea275b8ea1406e6cafea5583b9f8a5f000de14042756436303233");

        var addressUtxo = addressService
                .getAddressUtxosGivenAsset(JPG_CONTRACT_ADDRESS_V2, assetType.toUnit(), 1, 1, OrderEnum.asc)
                .getFirst();
        log.info("addressUtxo: {}", addressUtxo);

        var alwaysTrueFakeUtxo = Utxo.builder()
                .txHash(addressUtxo.getTxHash())
                .outputIndex(10)
                .address(alwaysTrueAddress.getAddress())
                .amount(List.of(Amount.ada(10)))
                .build();


        var listingDatumOpt = listingDatumService.findPlutusData(addressUtxo.getTxHash(), addressUtxo.getDataHash());
        if (listingDatumOpt.isEmpty()) {
            Assertions.fail();
        }

        var listingDatum = listingDatumOpt.get();
        log.info("listingDatum: {}", listingDatum.serializeToHex());

        var listingDetailsOpt = listingDatumParser.parseDatumV2(listingDatum.serializeToHex());

        if (listingDetailsOpt.isEmpty()) {
            Assertions.fail();
        }

        var listingDetails = listingDetailsOpt.get();
        log.info("listingDetails: {}", listingDetails);

        var utxo = bfBackendService.getUtxoService().getTxOutput(addressUtxo.getTxHash(), addressUtxo.getOutputIndex()).getValue();

        var walletUtxos = bfBackendService.getUtxoService().getUtxos(account.baseAddress(), 100, 1).getValue();

        //        jpg fees 7.98
//        difference = amount × (2/98)

        var bar = listingDetails.payees().stream().map(PaymentDetails::amount).reduce(Value::add).orElse(Value.builder().build()).getCoin();
        var jpgStoreFees = Rational.from(bar).multiply(Rational.from(2L, 98L)).floor();
        log.info("jpgStoreFees: {}", jpgStoreFees);

        final var tx = new ScriptTx()
                .collectFrom(walletUtxos)
                .collectFrom(alwaysTrueFakeUtxo)
                .collectFrom(utxo, ConstrPlutusData.of(0, BigIntPlutusData.of(0)), listingDatum)

                // fees
                .payToContract("addr1xxzvcf02fs5e282qk3pmjkau2emtcsj5wrukxak3np90n2evjel5h55fgjcxgchp830r7h2l5msrlpt8262r3nvr8eksg6pw3p", Amount.lovelace(jpgStoreFees), BytesPlutusData.of(HexUtil.decodeHexString("53cc96ff7c850f6b2a44c59ad463956251b684fc913cf7829c1e928dc822ab56")))
                .attachSpendingValidator(alwaysTrueScript);

        // payees
        listingDetails.payees().forEach(payee -> tx.payToAddress(payee.beneficiary(), ValueUtil.toAmountList(payee.amount())));

        tx.readFrom("1693c508b6132e89b932754d657d28b24068ff5ff1715fec36c010d4d6470b3d", 0)
                .withChangeAddress(account.baseAddress());


        var utxoSupplier = new DefaultUtxoSupplier(bfBackendService.getUtxoService()) {
            @Override
            public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
                if (alwaysTrueFakeUtxo.getTxHash().equals(txHash) && alwaysTrueFakeUtxo.equals(outputIndex)) {
                    return Optional.of(alwaysTrueFakeUtxo);
                }
                return super.getTxOutput(txHash, outputIndex);
            }
        };

        var scriptSupplier = new DefaultScriptSupplier(bfBackendService.getScriptService()) {
            @Override
            public Optional<PlutusScript> getScript(String scriptHash) {
                try {
                    if (alwaysTrueScript.getPolicyId().equals(scriptHash)) {
                        return Optional.of(alwaysTrueScript);
                    }
                } catch (Exception e) {
                    // do nothing
                }
                return super.getScript(scriptHash);
            }
        };

        var scalusScriptSupplier = new ScriptSupplier() {
            @Override
            public PlutusScript getScript(String scriptHash) {
                try {
                    if (alwaysTrueScript.getPolicyId().equals(scriptHash)) {
                        return alwaysTrueScript;
                    }
                } catch (Exception e) {
                    // do nothing
                }
                return scriptSupplier.getScript(scriptHash).orElse(null);
            }
        };

        var transactionEvaluator = new ScalusTransactionEvaluator(bfBackendService.getEpochService().getProtocolParameters().getValue(),
                utxoSupplier,
                scalusScriptSupplier);

        var transactionProcessor = new TransactionProcessor() {

            @Override
            public Result<List<EvaluationResult>> evaluateTx(byte[] cbor, Set<Utxo> inputUtxos) throws ApiException {
                return transactionEvaluator.evaluateTx(cbor, inputUtxos);
            }

            @Override
            public Result<String> submitTransaction(byte[] cborData) throws ApiException {
                return null;
            }
        };

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(utxoSupplier,
                new DefaultProtocolParamsSupplier(bfBackendService.getEpochService()),
                scriptSupplier,
                transactionProcessor);

        var transaction = quickTxBuilder.compose(tx)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .withTxEvaluator(transactionEvaluator)
                .preBalanceTx((txBuilderContext, transaction1) -> {
                    try {
                        log.info("pre tx: {}", OBJECT_MAPPER.writeValueAsString(transaction1));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .build();

        log.info("tx: {}", OBJECT_MAPPER.writeValueAsString(transaction));

    }

}
