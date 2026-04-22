package com.easy1staking.jpgstore.deployment;

import lombok.extern.slf4j.Slf4j;
import org.cardanofoundation.conversions.ClasspathConversionsFactory;
import org.cardanofoundation.conversions.domain.NetworkType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

@Slf4j
@Tag("deployment")
public class TimeConversionTest {

    @Test
    public void jpgStartTime() {

        var converter = ClasspathConversionsFactory.createConverters(NetworkType.MAINNET);
//        (Sep 30 2022, 22:24 GMT+1

        var startTime = LocalDateTime.of(2022, 9, 30, 21, 0);

        var epoch = converter.time().utcTimeToEpochNo(startTime);

        log.info("epoch: {}", epoch);
    }


}
