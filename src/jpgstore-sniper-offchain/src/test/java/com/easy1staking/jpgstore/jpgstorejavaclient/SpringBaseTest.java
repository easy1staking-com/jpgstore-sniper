package com.easy1staking.jpgstore.jpgstorejavaclient;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = {SpringBaseTest.TestConfig.class})
@ActiveProfiles("test")
public class SpringBaseTest {

    @Configuration
    @ComponentScan(basePackages = {"com.easy1staking.jpgstore.jpgstorejavaclient"})
    static class TestConfig {

    }

}
