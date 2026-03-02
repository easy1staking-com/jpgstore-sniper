package com.easy1staking.jpgstore.sniper.service;

import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SnipeRegistry {

    private final Map<String, List<TransactionInput>> policySnipes = new ConcurrentHashMap<>();

    private final Map<String, List<TransactionInput>> merkleSnipes = new ConcurrentHashMap<>();

    public void putPolicySnipe(String policyId, TransactionInput utxo) {
        var utxos = policySnipes.get(policyId);
        if (utxos == null) {
            var nodes = new ArrayList<TransactionInput>();
            nodes.add(utxo);
            policySnipes.put(policyId, nodes);
        } else {
            utxos.add(utxo);
        }
    }

    public List<TransactionInput> getPolicySnipe(String policyId) {
        return policySnipes.getOrDefault(policyId, List.of());
    }

    public void removePolicySnipe(String policyId) {
        policySnipes.remove(policyId);
    }

    public void clearPolicySnipes() {
        policySnipes.clear();
    }

    public void putMerkleSnipe(String nftAssetId, TransactionInput utxo) {
        var utxos = merkleSnipes.get(nftAssetId);
        if (utxos == null) {
            var nodes = new ArrayList<TransactionInput>();
            nodes.add(utxo);
            merkleSnipes.put(nftAssetId, nodes);
        } else {
            utxos.add(utxo);
        }
    }

    public List<TransactionInput> getMerkleSnipe(String nftAssetId) {
        return merkleSnipes.getOrDefault(nftAssetId, List.of());
    }

    public void removeMerkleSnipe(String nftAssetId) {
        merkleSnipes.remove(nftAssetId);
    }

    public void clearMerkleSnipes() {
        merkleSnipes.clear();
    }

    public void clearAll() {
        policySnipes.clear();
        merkleSnipes.clear();
    }

    public int getMerkleSize() {
        return merkleSnipes.size();
    }

    public int getPolicySize() {
        return policySnipes.size();
    }

}
