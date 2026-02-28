package com.easy1staking.jpgstore.deployment;

import com.bloxbean.cardano.aiken.AikenScriptUtil;
import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.util.ValueUtil;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.util.HexUtil;
import com.easy1staking.jpgstore.jpgstorejavaclient.AbstractTest;
import com.easy1staking.jpgstore.sniper.aiken.service.PlutusService;
import com.easy1staking.jpgstore.sniper.contract.SettingsContract;
import com.easy1staking.jpgstore.sniper.model.onchain.Address;
import com.easy1staking.jpgstore.sniper.model.onchain.AddressParser;
import com.easy1staking.jpgstore.sniper.model.onchain.SettingsParser;
import com.easy1staking.jpgstore.sniper.model.onchain.SnipeDatum;
import lombok.extern.slf4j.Slf4j;
import org.cardanofoundation.merkle.MerkleTree;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.math.BigInteger;
import java.util.List;

@Slf4j
public class PreviewMerkleSnipeDatumTest extends AbstractTest {

    private static final Network NETWORK = Networks.preview();

    @Test
    public void mintSnipeTest() throws Exception {

        var dryRun = false;

        var nfts = List.of("a5bb0e5bb275a573d744a021f9b3bff73595468e002755b447e01559484f534b594361736847726162303030313335373433",
                "a706fc87764cde4ac018c38bf61630c1065932db49e6f495be3b29f8436f636f4c6f636f426c756550616c6d53314e4654313737",
                "d0112837f8f856b2ca14f69b375bc394e73d146fdadcc993bb993779446973636f536f6c6172697335323839"
        );

        var addressParser = new AddressParser(OBJECT_MAPPER);

        var settingsParser = new SettingsParser(OBJECT_MAPPER, addressParser);

        var plutusService = new PlutusService(OBJECT_MAPPER, new ClassPathResource("./plutus.json"));

        var settingsContract = new SettingsContract(plutusService);
        log.info("settingsContract: {}", settingsContract.getScriptHash());

        var sniperAccount = Account.createFromMnemonic(NETWORK, MNEMONIC, 0, 0);
        var customerAccount = Account.createFromMnemonic(NETWORK, MNEMONIC, 1, 0);
        var treasuryAccount = Account.createFromMnemonic(NETWORK, MNEMONIC, 2, 0);

        log.info("sniperAccount: {}", sniperAccount.baseAddress());

        var merkleTree = MerkleTree.fromList(nfts, HexUtil::decodeHexString);

        var merkleTreeRoot = HexUtil.encodeHexString(merkleTree.itemHash());
        log.info("merkleTreeRoot: {}", merkleTreeRoot);

        var treasuryAddress = new Account();
        log.info("treasuryAddress: {}", treasuryAddress.baseAddress());

        var snipeCustomerAddress = new Address(customerAccount.getBaseAddress().getPaymentCredential().get(),
                customerAccount.getBaseAddress().getDelegationCredential().get());

        var policySnipeBytesOpt = plutusService.getContractCode("snipe.merkle_snipe.mint");
        if (policySnipeBytesOpt.isEmpty()) {
            Assertions.fail();
        }

        var policySnipeBytes = policySnipeBytesOpt.get();

        var policySnipeParameters = ListPlutusData.of(BytesPlutusData.of(settingsContract.getScriptHashBytes()));

        var policySnipeContract = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(
                AikenScriptUtil.applyParamToScript(policySnipeParameters, policySnipeBytes),
                PlutusVersion.v3
        );

        // FIXME: derive address and query via blockfrost
        var settingsUtxoOpt = bfBackendService.getUtxoService().getTxOutput("df691279a66621b267792e1f6e5d853b2a969236409ba16f526ba16e95f947ff", 0);
        if (!settingsUtxoOpt.isSuccessful()) {
            Assertions.fail();
        }

        var settingsUtxo = settingsUtxoOpt.getValue();
        log.info("settingsUtxo: {}", settingsUtxo);

        var settingsOpt = settingsParser.parse(settingsUtxo.getInlineDatum());
        if (settingsOpt.isEmpty()) {
            Assertions.fail();
        }

        var settings = settingsOpt.get();
        log.info("settings: {}", settings);

        var policySnipeAddress = AddressProvider.getBaseAddress(Credential.fromScript(policySnipeContract.getScriptHash()),
                settings.stakeCredential(),
                NETWORK);

        var policySnipe = SnipeDatum.builder()
                .ownerPkh(HexUtil.encodeHexString(customerAccount.getBaseAddress().getPaymentCredentialHash().get()))
                .nftDestination(snipeCustomerAddress)
                .targetHash(merkleTreeRoot)
                .maxPrice(10_000_000L)
                .protocolFee(1_000_000L)
                .build();

        var lockedAmount = Value.fromCoin(BigInteger.valueOf(13_000_000L))
                .add(Value.from(policySnipeContract.getPolicyId(), "0x", BigInteger.ONE));

        var customerUtxosOpt = bfBackendService.getUtxoService().getUtxos(customerAccount.baseAddress(), 100, 1);
        if (!customerUtxosOpt.isSuccessful()) {
            Assertions.fail();
        }

        var customerUtxos = customerUtxosOpt.getValue();

        var nftsByteList = nfts.stream().map(foo -> BytesPlutusData.of(HexUtil.decodeHexString(foo))).toList();

        var markleSnipeMintRedeemer = ConstrPlutusData.of(0,
                ListPlutusData.of(nftsByteList.toArray(new BytesPlutusData[]{}))
        );

        var tx = new ScriptTx()
                .collectFrom(customerUtxos)
                .mintAsset(policySnipeContract, Asset.builder().value(BigInteger.ONE).build(), markleSnipeMintRedeemer)
                .payToContract(policySnipeAddress.getAddress(), ValueUtil.toAmountList(lockedAmount), policySnipe.toPlutusData())
                .readFrom(settingsUtxo)
                .withChangeAddress(customerAccount.baseAddress());

        var transaction = QUICK_TX_BUILDER.compose(tx)
                .feePayer(customerAccount.baseAddress())
                .withTxEvaluator(new AikenTransactionEvaluator(bfBackendService))
                .withSigner(SignerProviders.signerFrom(customerAccount))
                .buildAndSign();

        log.info("transaction: {}", transaction.serializeToHex());

        log.info("transaction: {}", OBJECT_MAPPER.writeValueAsString(transaction));

        if (!dryRun) {
            var result = bfBackendService.getTransactionService().submitTransaction(transaction.serialize());
            if (result.isSuccessful()) {
                log.info("submitted tx: {}", result.getValue());
            } else {
                log.error("error: {}", result.getResponse());
                Assertions.fail();
            }
        }

    }

}
