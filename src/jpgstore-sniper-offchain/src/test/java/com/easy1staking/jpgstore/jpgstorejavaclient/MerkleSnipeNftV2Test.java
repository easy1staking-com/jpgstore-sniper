package com.easy1staking.jpgstore.jpgstorejavaclient;

import com.bloxbean.cardano.aiken.AikenScriptUtil;
import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.TransactionEvaluator;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.util.ValueUtil;
import com.bloxbean.cardano.client.backend.blockfrost.common.Constants;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.supplier.ogmios.OgmiosTransactionEvaluator;
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
import org.cardanofoundation.merkle.MerkleTree;
import org.cardanofoundation.merkle.ProofItem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.math.BigInteger;
import java.util.List;

import static com.easy1staking.jpgstore.sniper.model.Constants.JPG_CONTRACT_ADDRESS_V2;

@Slf4j
@Tag("deployment")
public class MerkleSnipeNftV2Test extends AbstractTest {

    private final Network network = Networks.mainnet();

    private final AddressService addressService = new AddressServiceImpl(Constants.BLOCKFROST_MAINNET_URL, BLOCKFROST_KEY);

    private final TransactionService transactionService = new TransactionServiceImpl(Constants.BLOCKFROST_MAINNET_URL, BLOCKFROST_KEY);

    private final ListingDatumParser listingDatumParser = new ListingDatumParser(OBJECT_MAPPER);

    private final ListingDatumService listingDatumService = new ListingDatumService(transactionService, OBJECT_MAPPER);

    @Test
    public void purchaseV2() throws Exception {

        var sniperAccount = Account.createFromMnemonic(Networks.mainnet(), MNEMONIC, 0, 0);

        var addressParser = new AddressParser(OBJECT_MAPPER);

        var settingsParser = new SettingsParser(OBJECT_MAPPER, addressParser);

        var snipeParser = new SnipeDatumParser(OBJECT_MAPPER, addressParser);

        var plutusService = new PlutusService(OBJECT_MAPPER, new ClassPathResource("./plutus.json"));

        var settingsContract = new SettingsContract(plutusService, "d444055834b0697b4db6c3385c4293d2a265b4a7bd549f14af6ee561d98a7976", 0);
        log.info("settingsContract: {}", settingsContract.getScriptHash());

        var snipeBytesOpt = plutusService.getContractCode("snipe.merkle_snipe.mint");
        if (snipeBytesOpt.isEmpty()) {
            Assertions.fail();
        }

        var snipeBytes = snipeBytesOpt.get();

        var snipeParameters = ListPlutusData.of(BytesPlutusData.of(settingsContract.getScriptHashBytes()));

        var snipeContract = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(
                AikenScriptUtil.applyParamToScript(snipeParameters, snipeBytes),
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

        var snipeAddress = AddressProvider.getBaseAddress(Credential.fromScript(snipeContract.getScriptHash()),
                settings.stakeCredential(),
                network);

        var nfts = List.of("a5bb0e5bb275a573d744a021f9b3bff73595468e002755b447e01559484f534b594361736847726162303030313335373433",
                "a706fc87764cde4ac018c38bf61630c1065932db49e6f495be3b29f8436f636f4c6f636f426c756550616c6d53314e4654313737",
                "d0112837f8f856b2ca14f69b375bc394e73d146fdadcc993bb993779446973636f536f6c6172697335323839"
        );

        var merkleTree = MerkleTree.fromList(nfts, HexUtil::decodeHexString);

        var merkleTreeRoot = HexUtil.encodeHexString(merkleTree.itemHash());
        log.info("merkleTreeRoot: {}", merkleTreeRoot);

        var assetType = AssetType.fromUnit("a5bb0e5bb275a573d744a021f9b3bff73595468e002755b447e01559484f534b594361736847726162303030313335373433");

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


        var snipeUtxo = bfBackendService.getUtxoService().getUtxos(snipeAddress.getAddress(), 100, 1).getValue().getFirst();
        log.info("snipeUtxo: {}", snipeUtxo);

        var snipeDatum = snipeParser.parse(snipeUtxo.getInlineDatum()).get();
        var snipeTag = getUtxoHash(snipeUtxo.getTxHash(), snipeUtxo.getOutputIndex());

        var snipeAddressPkh = new Address(snipeUtxo.getAddress()).getPaymentCredentialHash().map(HexUtil::encodeHexString).get();
        log.info("snipeAddressPkh: {}", snipeAddressPkh);
        log.info("policyIdSnipeContract: {}", snipeContract.getScriptHash());


        var nftDestination = snipeDatum.nftDestination().toAddress(network);

        var treasuryAddress = settings.protocolTreasury().toAddress(network);

        var proofOpt = MerkleTree.getProof(merkleTree, assetType.toUnit(), HexUtil::decodeHexString);
        if (proofOpt.isEmpty()) {
            log.warn("could not compute proof");
            Assertions.fail();
        }

        var proof = proofOpt.get();
        log.info("proof: {}", proof);

        var proofList = proof.toJavaList()
                .stream()
                .map(proofItem -> switch (proofItem) {
                    case ProofItem.Left left ->
                            ConstrPlutusData.of(0, ConstrPlutusData.of(0, BytesPlutusData.of(left.getHash())));
                    case ProofItem.Right right ->
                            ConstrPlutusData.of(1, ConstrPlutusData.of(0, BytesPlutusData.of(right.getHash())));
                    default -> throw new IllegalArgumentException();
                })
                .toList();

        var snipeRedeemer = ConstrPlutusData.of(0,
                BytesPlutusData.of(HexUtil.decodeHexString(assetType.policyId())),
                BytesPlutusData.of(HexUtil.decodeHexString(assetType.assetName())),
                ListPlutusData.of(proofList.toArray(new PlutusData[]{}))
        );

        var tx = new ScriptTx()
                // Collect SNIPE
                .collectFrom(snipeUtxo, snipeRedeemer)
                // Collect NFT
                .collectFrom(utxo, ConstrPlutusData.of(0, BigIntPlutusData.of(0)), listingDatum)
                // BURN Snipe NFT
                .mintAsset(snipeContract, Asset.builder().value(BigInteger.ONE.negate()).build(), ConstrPlutusData.of(1))
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
                .withTxEvaluator(ogmiosTE())
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

    public TransactionEvaluator ogmiosTE() {
        return new OgmiosTransactionEvaluator("http://panic-station:31337");
    }

    public PlutusData getUtxoHash(String txHash, int outputIndex) {
        var outRefHash = ConstrPlutusData.of(0,
                BytesPlutusData.of(HexUtil.decodeHexString(txHash)),
                BigIntPlutusData.of(outputIndex)
        );
        return BytesPlutusData.of(outRefHash.getDatumHashAsBytes());
    }


}
