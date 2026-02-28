package com.easy1staking.jpgstore.jpgstorejavaclient;

import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractTest {

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected static final String MNEMONIC = System.getenv("WALLET_MNEMONIC");

    protected static final String BLOCKFROST_URL = System.getenv("BLOCKFROST_URL");

    protected static final String BLOCKFROST_KEY = System.getenv("BLOCKFROST_KEY");

    protected static final BFBackendService bfBackendService = new BFBackendService(BLOCKFROST_URL, BLOCKFROST_KEY);

    protected static final QuickTxBuilder QUICK_TX_BUILDER = new QuickTxBuilder(bfBackendService);


}
