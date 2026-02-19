package com.easy1staking.jpgstore.sniper.util;

import com.bloxbean.cardano.client.plutus.spec.*;

public class PlutusDataUtil {

    public static PlutusData plutusDataValue(Long lovelaces) {
        MapPlutusData innerMapPlutusData = new MapPlutusData();

        innerMapPlutusData.put(BytesPlutusData.of(""), BigIntPlutusData.of(lovelaces));

        MapPlutusData mapPlutusData = new MapPlutusData();
        mapPlutusData.put(BytesPlutusData.of(""),
                ConstrPlutusData.of(0,
                        BigIntPlutusData.of(0),
                        innerMapPlutusData
                )
        );
        return mapPlutusData;
    }

}
