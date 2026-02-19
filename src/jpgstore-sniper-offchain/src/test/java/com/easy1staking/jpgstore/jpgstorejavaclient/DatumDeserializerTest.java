package com.easy1staking.jpgstore.jpgstorejavaclient;


import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.easy1staking.jpgstore.sniper.config.BlockfrostConfig;
import com.easy1staking.jpgstore.sniper.service.ListingDatumService;
import com.easy1staking.jpgstore.sniper.service.ListingService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.InputStream;

@SpringBootTest(classes = {BlockfrostConfig.class,  ListingService.class, ListingDatumService.class})
public class DatumDeserializerTest {

    @Test
    public void parsePrices() throws IOException {

        InputStream resourceAsStream = this.getClass()
                .getClassLoader()
                .getResourceAsStream("./d0112837f8f856b2ca14f69b375bc394e73d146fdadcc993bb993779-DiscoSolaris1078.json");

        String s = new String(resourceAsStream.readAllBytes());
//        System.out.println(s);

        JsonNode jsonNode = JsonUtil.parseJson(s);
//        System.out.println(jsonNode.path("constructor"));
        Long jpgStoreFee = jsonNode
                .path("fields")
                .get(1)
                .path("list")
                .get(0)
                .path("fields")
                .get(1)
                .path("map")
                .get(0)
                .path("v")
                .path("fields")
                .get(1)
                .path("map")
                .get(0)
                .path("v")
                .path("int")
                .asLong();

        Long royalties = jsonNode
                .path("fields")
                .get(1)
                .path("list")
                .get(1)
                .path("fields")
                .get(1)
                .path("map")
                .get(0)
                .path("v")
                .path("fields")
                .get(1)
                .path("map")
                .get(0)
                .path("v")
                .path("int")
                .asLong();

        Long nftPrice = jsonNode
                .path("fields")
                .get(1)
                .path("list")
                .get(2)
                .path("fields")
                .get(1)
                .path("map")
                .get(0)
                .path("v")
                .path("fields")
                .get(1)
                .path("map")
                .get(0)
                .path("v")
                .path("int")
                .asLong();
        System.out.println("jpg store: " + jpgStoreFee);
        System.out.println("royalties: " + royalties);
        System.out.println("nftPrice: " + nftPrice);

        long totalPrice = jpgStoreFee + royalties + nftPrice;
        System.out.println(totalPrice / 1000000l);
    }

    @Test
    public void parseRoyaltiesAddress() throws IOException {
        InputStream resourceAsStream = this.getClass()
                .getClassLoader()
                .getResourceAsStream("./d0112837f8f856b2ca14f69b375bc394e73d146fdadcc993bb993779-DiscoSolaris1078.json");

        String s = new String(resourceAsStream.readAllBytes());

        JsonNode jsonNode = JsonUtil.parseJson(s);
//addr1xy8gs8hm7d08qwc7cc2e6d9zdnx59ukxpjuvt5xpapj669cw3q00hu67wqa3a3s4n562ymxdgtevvr9cchgvr6r945ts486dxx
//        0e881efbf35e703b1ec6159d34a26ccd42f2c60cb8c5d0c1e865ad17
//        0e881efbf35e703b1ec6159d34a26ccd42f2c60cb8c5d0c1e865ad17

        String pkhText = jsonNode
                .path("fields")
                .get(1)
                .path("list")
                .get(1)
                .path("fields")
                .get(0)
                .path("fields")
                .get(0)
                .path("fields")
                .get(0)
                .path("bytes")
                .asText();

        System.out.println("pkhText: " + pkhText);
        Credential pkh = Credential.fromKey(pkhText);
        Credential stkCred = Credential.fromKey(pkhText);

        Address baseAddress = AddressProvider.getBaseAddress(pkh, stkCred, Networks.mainnet());
        System.out.println("baseAddress: " + baseAddress.getAddress());

//        Address address = new Address("addr1xy8gs8hm7d08qwc7cc2e6d9zdnx59ukxpjuvt5xpapj669cw3q00hu67wqa3a3s4n562ymxdgtevvr9cchgvr6r945ts486dxx");
        Address address = new Address("addr1qy8gs8hm7d08qwc7cc2e6d9zdnx59ukxpjuvt5xpapj669cw3q00hu67wqa3a3s4n562ymxdgtevvr9cchgvr6r945tsykcfdn");
        System.out.println(address.getAddressType());
        address.getPaymentCredentialHash()
                .ifPresent(hash1 -> System.out.println(HexUtil.encodeHexString(hash1)));
        address.getDelegationCredentialHash()
                .ifPresent(hash2 -> System.out.println(HexUtil.encodeHexString(hash2)));
//
    }

    @Test
    public void addressTest() {
//        df7ddac173dd84c715cdcedaa86915e0625768f7a2dcfa7dbdec64c7
//        "bytes": "a983482b22919297960fca90526b2187929c9fde1832bd3d23fdd7f1"

//        Address address = new Address("df7ddac173dd84c715cdcedaa86915e0625768f7a2dcfa7dbdec64c7".getBytes());
        Credential pkh = Credential.fromKey("df7ddac173dd84c715cdcedaa86915e0625768f7a2dcfa7dbdec64c7");
        Credential stkCred = Credential.fromKey("a983482b22919297960fca90526b2187929c9fde1832bd3d23fdd7f1");
//        HdPublicKey pkh = HdPublicKey.fromBytes(HexUtil.decodeHexString("df7ddac173dd84c715cdcedaa86915e0625768f7a2dcfa7dbdec64c7"));
//        HdPublicKey skCred = HdPublicKey.fromBytes(HexUtil.decodeHexString("df7ddac173dd84c715cdcedaa86915e0625768f7a2dcfa7dbdec64c7"));

        Address baseAddress = AddressProvider.getBaseAddress(pkh, stkCred, Networks.mainnet());
        System.out.println(baseAddress.getAddress());
//        addr1q80hmkkpw0wcf3c4eh8d42rfzhsxy4mg773de7nahhkxf3afsdyzkg53j2tevr72jpfxkgv8j2wflhscx27n6gla6lcs6ygsda
//        addr1q80hmkkpw0wcf3c4eh8d42rfzhsxy4mg773de7nahhkxf3afsdyzkg53j2tevr72jpfxkgv8j2wflhscx27n6gla6lcs6ygsda

    }

    @Test
    public void resolveFeeDatum() {
        var datum ="53cc96ff7c850f6b2a44c59ad463956251b684fc913cf7829c1e928dc822ab56";

    }

}
