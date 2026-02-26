package com.easy1staking.jpgstore.sniper.service;

import com.bloxbean.cardano.yaci.core.model.TransactionInput;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SnipeRegistry {

    private final Map<String, TransactionInput> policySnipes = new ConcurrentHashMap<>();

    private final Map<String, TransactionInput> merkleSnipes = new ConcurrentHashMap<>();

    public void putPolicySnipe(String policyId, TransactionInput utxo) {
        policySnipes.put(policyId, utxo);
    }

    public Optional<TransactionInput> getPolicySnipe(String policyId) {
        return Optional.ofNullable(policySnipes.get(policyId));
    }

    public void removePolicySnipe(String policyId) {
        policySnipes.remove(policyId);
    }

    public void clearPolicySnipes() {
        policySnipes.clear();
    }

    public void putMerkleSnipe(String nftAssetId, TransactionInput utxo) {
        merkleSnipes.put(nftAssetId, utxo);
    }

    public Optional<TransactionInput> getMerkleSnipe(String nftAssetId) {
        return Optional.ofNullable(merkleSnipes.get(nftAssetId));
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

}
