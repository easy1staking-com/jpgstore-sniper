package com.easy1staking.jpgstore.sniper.model.contract.v2;

import com.bloxbean.cardano.client.transaction.spec.Value;
import com.easy1staking.cardano.model.AssetType;
import com.easy1staking.util.Pair;

import java.math.BigInteger;
import java.util.List;

public record PaymentDetails(String beneficiary, List<Pair<AssetType, Long>> assets) {

    public Value amount() {
        return assets.stream()
                .reduce(Value.builder().build(), (value, pair) -> {
                    var assetType = pair.first();
                    if (assetType.isAda()) {
                        return value.addCoin(BigInteger.valueOf(pair.second()));
                    } else {
                        return value.add(assetType.policyId(), "0x" + assetType.assetName(), BigInteger.valueOf(pair.second()));
                    }
                }, Value::add);
    }

}
