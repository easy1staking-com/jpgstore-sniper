package com.easy1staking.jpgstore.jpgstorejavaclient;


import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class SimpleDatumTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    public void resolveFeeDatum() throws Exception {
        var datum = "53cc96ff7c850f6b2a44c59ad463956251b684fc913cf7829c1e928dc822ab56";
        var data = PlutusData.deserialize(HexUtil.decodeHexString(datum));
        log.info("datum: {}", OBJECT_MAPPER.writeValueAsString(data));
    }

    @Test
    public void resolveFeeDatum1() throws Exception {
        var datum = "53cc96ff7c850f6b2a44c59ad463956251b684fc913cf7829c1e928dc822ab56";
        var data = new String(HexUtil.decodeHexString(datum));
        log.info("datum: {}", data);
    }

}
