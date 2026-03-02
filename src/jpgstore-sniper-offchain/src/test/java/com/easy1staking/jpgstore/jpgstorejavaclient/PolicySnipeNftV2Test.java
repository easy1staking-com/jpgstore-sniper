package com.easy1staking.jpgstore.jpgstorejavaclient;

import com.bloxbean.cardano.aiken.AikenScriptUtil;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
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
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.util.HexUtil;
import com.easy1staking.cardano.model.AssetType;
import com.easy1staking.jpgstore.sniper.aiken.service.PlutusService;
import com.easy1staking.jpgstore.sniper.contract.SettingsContract;
import com.easy1staking.jpgstore.sniper.model.contract.v2.PaymentDetails;
import com.easy1staking.jpgstore.sniper.model.onchain.AddressParser;
import com.easy1staking.jpgstore.sniper.model.onchain.SettingsParser;
import com.easy1staking.jpgstore.sniper.model.onchain.SnipeDatumParser;
import com.easy1staking.jpgstore.sniper.service.ListingDatumParser;
import com.easy1staking.jpgstore.sniper.service.ListingDatumService;
import com.easy1staking.math.Rational;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.blockfrost.sdk.api.AddressService;
import io.blockfrost.sdk.api.TransactionService;
import io.blockfrost.sdk.api.util.OrderEnum;
import io.blockfrost.sdk.impl.AddressServiceImpl;
import io.blockfrost.sdk.impl.TransactionServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import scalus.bloxbean.ScalusTransactionEvaluator;
import scalus.bloxbean.ScriptSupplier;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.easy1staking.jpgstore.sniper.model.Constants.JPG_CONTRACT_ADDRESS_V2;

@Slf4j
public class PolicySnipeNftV2Test extends AbstractTest {

    private final Network network = Networks.mainnet();

    private final AddressService addressService = new AddressServiceImpl(Constants.BLOCKFROST_MAINNET_URL, BLOCKFROST_KEY);

    private final TransactionService transactionService = new TransactionServiceImpl(Constants.BLOCKFROST_MAINNET_URL, BLOCKFROST_KEY);

    private final ListingDatumParser listingDatumParser = new ListingDatumParser(OBJECT_MAPPER);

    private final ListingDatumService listingDatumService = new ListingDatumService(transactionService, OBJECT_MAPPER);

    @Test
    public void purchaseV2() throws Exception {

        var sniperAccount = Account.createFromMnemonic(Networks.mainnet(), MNEMONIC, 0, 0);
        var customerAccount = Account.createFromMnemonic(Networks.mainnet(), MNEMONIC, 1, 0);
        var treasuryAccount = Account.createFromMnemonic(Networks.mainnet(), MNEMONIC, 2, 0);

        var addressParser = new AddressParser(OBJECT_MAPPER);

        var settingsParser = new SettingsParser(OBJECT_MAPPER, addressParser);

        var snipeParser = new SnipeDatumParser(OBJECT_MAPPER, addressParser);

        var plutusService = new PlutusService(OBJECT_MAPPER, new ClassPathResource("./plutus.json"));

        var settingsContract = new SettingsContract(plutusService, "d444055834b0697b4db6c3385c4293d2a265b4a7bd549f14af6ee561d98a7976", 0);
        log.info("settingsContract: {}", settingsContract.getScriptHash());

        var policySnipeBytesOpt = plutusService.getContractCode("snipe.policy_snipe.mint");
        if (policySnipeBytesOpt.isEmpty()) {
            Assertions.fail();
        }

        var policySnipeBytes = policySnipeBytesOpt.get();

        var policySnipeParameters = ListPlutusData.of(BytesPlutusData.of(settingsContract.getScriptHashBytes()));

        var policySnipeContract = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(
                AikenScriptUtil.applyParamToScript(policySnipeParameters, policySnipeBytes),
                PlutusVersion.v3
        );

        var settingsAddress = AddressProvider.getEntAddress(settingsContract.getPlutusScript(), network);
        var settingsUtxoOpt = bfBackendService.getUtxoService().getUtxos(settingsAddress.getAddress(), 100, 1);
        if (!settingsUtxoOpt.isSuccessful()) {
            Assertions.fail();
        }

        var settingsUtxo = settingsUtxoOpt.getValue().getFirst();
        log.info("settingsUtxo: {}", settingsUtxo);

        var settingsOpt = settingsParser.parse(settingsUtxo.getInlineDatum());
        if (settingsOpt.isEmpty()) {
            Assertions.fail();
        }

        var settings = settingsOpt.get();
        log.info("settings: {}", settings);

        var policySnipeAddress = AddressProvider.getBaseAddress(Credential.fromScript(policySnipeContract.getScriptHash()),
                settings.stakeCredential(),
                network);

        var assetType = AssetType.fromUnit("a5bb0e5bb275a573d744a021f9b3bff73595468e002755b447e01559484f534b594361736847726162303030303836333830");

        var addressUtxo = addressService
                .getAddressUtxosGivenAsset(JPG_CONTRACT_ADDRESS_V2, assetType.toUnit(), 1, 1, OrderEnum.asc)
                .getFirst();
        log.info("addressUtxo: {}", addressUtxo);

        var outRefHash = ConstrPlutusData.of(0,
                ConstrPlutusData.of(0, BytesPlutusData.of(HexUtil.decodeHexString(addressUtxo.getTxHash()))),
                BigIntPlutusData.of(addressUtxo.getOutputIndex())
        );

        var txFeeDatum = BytesPlutusData.of(outRefHash.getDatumHashAsBytes());

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

        var walletUtxos = bfBackendService.getUtxoService().getUtxos(sniperAccount.baseAddress(), 100, 1).getValue();

        var datumHash = listingDatum.getDatumHash();
        log.info("datumHash: {}", datumHash);

        var bar = listingDetails.payees().stream().map(PaymentDetails::amount).reduce(Value::add).orElse(Value.builder().build()).getCoin();
        var jpgStoreFees1 = Rational.from(bar).multiply(Rational.from(20L, 980L)).floor();
        log.info("jpgStoreFees1: {}", jpgStoreFees1);

        var actualJpgStoreFees = BigInteger.valueOf(1_000_000L).max(jpgStoreFees1);
        log.info("actualJpgStoreFees: {}", actualJpgStoreFees);


        var snipeUtxo = bfBackendService.getUtxoService().getUtxos(policySnipeAddress.getAddress(), 100, 1).getValue().getFirst();
        log.info("snipeUtxo: {}", snipeUtxo);

        var snipeDatum = snipeParser.parse(snipeUtxo.getInlineDatum()).get();
        var snipeTag = getUtxoHash(snipeUtxo.getTxHash(), snipeUtxo.getOutputIndex());

        var snipeAddressPkh = new Address(snipeUtxo.getAddress()).getPaymentCredentialHash().map(HexUtil::encodeHexString).get();
        log.info("snipeAddressPkh: {}", snipeAddressPkh);
        log.info("policyIdSnipeContract: {}", policySnipeContract.getScriptHash());


        var nftDestinationAddress = snipeDatum.nftDestination();

        Address nftDestination;
        if (nftDestinationAddress.stakeKeyHash() != null) {
            nftDestination = AddressProvider.getBaseAddress(nftDestinationAddress.paymentKeyHash(), nftDestinationAddress.stakeKeyHash(), network);
        } else {
            nftDestination = AddressProvider.getEntAddress(nftDestinationAddress.paymentKeyHash(), network);
        }

        var protocolTreasuryAddress = settings.protocolTreasury();

        var treasuryAddress = AddressProvider.getBaseAddress(protocolTreasuryAddress.paymentKeyHash(), protocolTreasuryAddress.stakeKeyHash(), network);

        var tx = new ScriptTx()
//                .collectFrom(walletUtxos)
                // Collect SNIPE
                .collectFrom(snipeUtxo, ConstrPlutusData.of(0))
                // Collect NFT
                .collectFrom(utxo, ConstrPlutusData.of(0, BigIntPlutusData.of(0)), listingDatum)
                // BURN Snipe NFT
                .mintAsset(policySnipeContract, Asset.builder().value(BigInteger.ONE.negate()).build(), ConstrPlutusData.of(0))
// Pay JPG Store Fees
                .payToContract("addr1xxzvcf02fs5e282qk3pmjkau2emtcsj5wrukxak3np90n2evjel5h55fgjcxgchp830r7h2l5msrlpt8262r3nvr8eksg6pw3p", Amount.lovelace(actualJpgStoreFees), txFeeDatum);

        // payees
        listingDetails.payees()
                .forEach(payee -> {
                    log.info("payee: {}, amount: {}", payee.beneficiary(), payee.amount());
                    tx.payToAddress(payee.beneficiary(), ValueUtil.toAmountList(payee.amount()));
                });

        tx // Deliver NFT
                .payToContract(nftDestination.getAddress(), Amount.asset(assetType.toUnit(), BigInteger.ONE), snipeTag)
                // Pay Sniper Protocol
                .payToContract(treasuryAddress.getAddress(), Amount.ada(1L), snipeTag)
                .readFrom(settingsUtxo)
                .readFrom("1693c508b6132e89b932754d657d28b24068ff5ff1715fec36c010d4d6470b3d", 0)
                .withChangeAddress(sniperAccount.baseAddress());

        QuickTxBuilder quickTxBuilder = new QuickTxBuilder(bfBackendService);

        var transaction = quickTxBuilder.compose(tx)
                .feePayer(sniperAccount.baseAddress())
                .withSigner(SignerProviders.signerFrom(sniperAccount))
                .preBalanceTx((txBuilderContext, transaction1) -> {
                    try {
                        log.info("pre tx: {}", OBJECT_MAPPER.writeValueAsString(transaction1));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .mergeOutputs(false)
                .completeAndWait();

        log.info("tx: {}", OBJECT_MAPPER.writeValueAsString(transaction));

    }

    public PlutusData getUtxoHash(String txHash, int outputIndex) {
        var outRefHash = ConstrPlutusData.of(0,
                BytesPlutusData.of(HexUtil.decodeHexString(txHash)),
                BigIntPlutusData.of(outputIndex)
        );
        return BytesPlutusData.of(outRefHash.getDatumHashAsBytes());
    }

}
