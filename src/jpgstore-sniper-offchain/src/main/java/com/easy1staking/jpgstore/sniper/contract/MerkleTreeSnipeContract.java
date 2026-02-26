package com.easy1staking.jpgstore.sniper.contract;

import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.easy1staking.jpgstore.sniper.aiken.model.AbstractContract;
import com.easy1staking.jpgstore.sniper.aiken.service.PlutusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MerkleTreeSnipeContract extends AbstractContract {

    public MerkleTreeSnipeContract(PlutusService plutusService) {
        super("snipe.merkle_snipe.mint", ListPlutusData.of(), plutusService, PlutusVersion.v3);
    }

}
