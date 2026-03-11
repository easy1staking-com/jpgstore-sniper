package com.easy1staking.jpgstore.deployment;

import com.bloxbean.cardano.aiken.AikenScriptUtil;
import com.bloxbean.cardano.aiken.AikenTransactionEvaluator;
import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.api.util.ValueUtil;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.plutus.blueprint.PlutusBlueprintUtil;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.Value;
import com.bloxbean.cardano.client.util.HexUtil;
import com.easy1staking.jpgstore.jpgstorejavaclient.AbstractTest;
import com.easy1staking.jpgstore.sniper.aiken.service.PlutusService;
import com.easy1staking.jpgstore.sniper.model.onchain.Address;
import com.easy1staking.jpgstore.sniper.model.onchain.Settings;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.math.BigInteger;

@Slf4j
@Tag("deployment")
public class MainnetProtocolDeploymentTest extends AbstractTest {

    private static final Network NETWORK = Networks.mainnet();

    @Test
    public void deploySettings() throws Exception {

        var dryRun = false;

        var plutusService = new PlutusService(OBJECT_MAPPER, new ClassPathResource("./plutus.json"));

        var settingsBytesOpt = plutusService.getContractCode("settings.settings.mint");
        if (settingsBytesOpt.isEmpty()) {
            Assertions.fail();
        }
        var settingsBytes = settingsBytesOpt.get();

        var sniperAccount = Account.createFromMnemonic(NETWORK, MNEMONIC, 0, 0);
        log.info("sniperAccount: {}", sniperAccount.baseAddress());

        var customerAccount = Account.createFromMnemonic(NETWORK, MNEMONIC, 1, 0);
        log.info("customerAccount: {}", customerAccount.baseAddress());

        var treasuryAccount = Account.createFromMnemonic(NETWORK, MNEMONIC, 2, 0);
        log.info("treasuryAccount: {}", treasuryAccount.baseAddress());

        var treasuryAddress = treasuryAccount.getBaseAddress();

        var treasuryUtxosOpt = bfBackendService.getUtxoService().getUtxos(treasuryAddress.getAddress(), 100, 1);

        if (!treasuryUtxosOpt.isSuccessful()) {
            Assertions.fail();
        }

        var treasuryUtxos = treasuryUtxosOpt.getValue();
        var bootstrapUtxo = treasuryUtxos.getFirst();
        log.info("bootstrapUtxo: {}", bootstrapUtxo);

        var settingsParameters = ListPlutusData.of(
                ConstrPlutusData.of(0,
                        BytesPlutusData.of(HexUtil.decodeHexString(bootstrapUtxo.getTxHash())),
                        BigIntPlutusData.of(bootstrapUtxo.getOutputIndex())
                )
        );

        var settingsContract = PlutusBlueprintUtil.getPlutusScriptFromCompiledCode(
                AikenScriptUtil.applyParamToScript(settingsParameters, settingsBytes),
                PlutusVersion.v3
        );

        var settingsAddress = AddressProvider.getEntAddress(settingsContract, NETWORK);

        var settingsHex = HexUtil.encodeHexString("settings".getBytes());

        log.info("policy id: {}, asset name: {}", settingsContract.getPolicyId(), settingsHex);

        var settingValue = Value.from(settingsContract.getPolicyId(), "0x" + settingsHex, BigInteger.ONE);

        var settingsDatum = Settings.builder()
                .operatorFeePct(1)
                .protocolFeePct(1)
                .minOperatorFee(1_000_000L)
                .minProtocolFee(1_000_000L)
                .protocolTreasury(new Address(treasuryAddress.getPaymentCredential().get(), treasuryAddress.getDelegationCredential().get()))
                .stakeCredential(treasuryAddress.getDelegationCredential().get())
                .txFeeBudget(1_000_000L)
                .adminPkh(treasuryAddress.getPaymentCredentialHash().map(HexUtil::encodeHexString).get())
                .build();

        var tx = new ScriptTx()
                .collectFrom(bootstrapUtxo)
                .mintAsset(settingsContract, Asset.builder().name("0x" + settingsHex).value(BigInteger.ONE).build(), ConstrPlutusData.of(0))
                .payToContract(settingsAddress.getAddress(), ValueUtil.toAmountList(settingValue), settingsDatum.toPlutusData())
                .withChangeAddress(treasuryAddress.getAddress());

        var transaction = new QuickTxBuilder(bfBackendService)
                .compose(tx)
                .withRequiredSigners(treasuryAddress)
                .feePayer(treasuryAddress.getAddress())
                .withSigner(SignerProviders.signerFrom(treasuryAccount))
                .withTxEvaluator(new AikenTransactionEvaluator(bfBackendService))
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
