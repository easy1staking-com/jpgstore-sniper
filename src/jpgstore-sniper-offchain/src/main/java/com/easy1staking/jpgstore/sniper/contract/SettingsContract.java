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
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SettingsContract extends AbstractContract {

    private static final ListPlutusData PARAMETERS = ListPlutusData.of(
            ConstrPlutusData.of(0,
                    BytesPlutusData.of(HexUtil.decodeHexString("3f59fd0b05d4755c72879372a903d61fb67f30be5d925267cc95a06d229f9971")),
                    BigIntPlutusData.of(1L)
            )
    );

    public SettingsContract(PlutusService plutusService) {
        super("settings.settings.mint", PARAMETERS, plutusService, PlutusVersion.v3);
    }

}
