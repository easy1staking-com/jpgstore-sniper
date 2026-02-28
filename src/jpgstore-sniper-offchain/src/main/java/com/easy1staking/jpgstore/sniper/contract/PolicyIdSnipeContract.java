package com.easy1staking.jpgstore.sniper.contract;

import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.easy1staking.jpgstore.sniper.aiken.model.AbstractContract;
import com.easy1staking.jpgstore.sniper.aiken.service.PlutusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PolicyIdSnipeContract extends AbstractContract {

    public PolicyIdSnipeContract(PlutusService plutusService, SettingsContract settingsContract) {
        super("snipe.policy_snipe.mint", ListPlutusData.of(BytesPlutusData.of(settingsContract.getScriptHashBytes())), plutusService, PlutusVersion.v3);
    }

}
