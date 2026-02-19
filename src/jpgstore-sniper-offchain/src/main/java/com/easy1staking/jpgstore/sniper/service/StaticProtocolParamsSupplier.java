package com.easy1staking.jpgstore.sniper.service;

import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StaticProtocolParamsSupplier implements ProtocolParamsSupplier {

    private final BFBackendService backendService;

    private ProtocolParams protocolParams;

    @Override
    public ProtocolParams getProtocolParams() {
        return protocolParams;
    }

    @PostConstruct
    public void preloadProtocolParams() {
        try {
            protocolParams = backendService.getEpochService().getProtocolParameters().getValue();
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }

}
