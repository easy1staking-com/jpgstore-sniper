package com.easy1staking.jpgstore.sniper.model;

import com.bloxbean.cardano.client.transaction.spec.TransactionInput;

public interface Constants {

    String JPG_CONTRACT_ADDRESS_V1 = "addr1zxgx3far7qygq0k6epa0zcvcvrevmn0ypsnfsue94nsn3tvpw288a4x0xf8pxgcntelxmyclq83s0ykeehchz2wtspks905plm";

    String JPG_CONTRACT_ADDRESS_V2 = "addr1x8rjw3pawl0kelu4mj3c8x20fsczf5pl744s9mxz9v8n7efvjel5h55fgjcxgchp830r7h2l5msrlpt8262r3nvr8ekstg4qrx";

    String JPG_STORE_FEE_ADDRESS = "addr1xxzvcf02fs5e282qk3pmjkau2emtcsj5wrukxak3np90n2evjel5h55fgjcxgchp830r7h2l5msrlpt8262r3nvr8eksg6pw3p";

    TransactionInput JPG_STORE_V2_CONTRACT_REF_INPUT = TransactionInput.builder()
            .transactionId("1693c508b6132e89b932754d657d28b24068ff5ff1715fec36c010d4d6470b3d")
            .index(0)
            .build();

}
