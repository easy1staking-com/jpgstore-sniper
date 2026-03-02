package com.easy1staking.jpgstore.sniper.contract;

import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.easy1staking.jpgstore.sniper.aiken.model.AbstractContract;
import com.easy1staking.jpgstore.sniper.aiken.service.PlutusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SettingsContract extends AbstractContract {

    public SettingsContract(PlutusService plutusService,
                            @Value("${bootstrap.settings.utxo.txHash}") String txHash,
                            @Value("${bootstrap.settings.utxo.index}") int outputIndex) {
        super("settings.settings.mint", ListPlutusData.of(
                ConstrPlutusData.of(0,
                        BytesPlutusData.of(HexUtil.decodeHexString(txHash)),
                        BigIntPlutusData.of(outputIndex)
                )
        ), plutusService, PlutusVersion.v3);
    }

}
