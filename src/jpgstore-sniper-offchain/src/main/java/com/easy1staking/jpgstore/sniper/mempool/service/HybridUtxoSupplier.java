package com.easy1staking.jpgstore.sniper.mempool.service;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.Vector;

@Slf4j
public class HybridUtxoSupplier extends DefaultUtxoSupplier {

    private final List<Utxo> mempoolUtxo = new Vector<>();

    public HybridUtxoSupplier(UtxoService utxoService) {
        super(utxoService);
    }

    @Override
    public List<Utxo> getPage(String address, Integer nrOfItems, Integer page, OrderEnum order) {
        var utxos = mempoolUtxo.stream().filter(utxo -> address.equals(utxo.getAddress())).toList();
        if (utxos.isEmpty() || page > 1) {
            return super.getPage(address, nrOfItems, page, order);
        } else {
            return utxos;
        }
    }

    @Override
    public Optional<Utxo> getTxOutput(String txHash, int outputIndex) {
        log.info("Get tx output: {}:{}", txHash, outputIndex);
        var result = mempoolUtxo.stream()
                .filter(utxo -> utxo.getTxHash().equals(txHash) && utxo.getOutputIndex() == outputIndex)
                .findFirst()
                .or(() -> super.getTxOutput(txHash, outputIndex));
        if (result.isEmpty()) {
            log.warn("could not resolve: {}:{}", txHash, outputIndex);
        }
        return result;
    }

    public void add(Utxo utxo) {
        log.info("adding utxo: {}", utxo);
        mempoolUtxo.add(utxo);
    }

    public void clear() {
        log.info("clearing utxos");
        mempoolUtxo.clear();
    }

}