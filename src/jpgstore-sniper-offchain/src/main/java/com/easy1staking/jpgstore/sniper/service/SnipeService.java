package com.easy1staking.jpgstore.sniper.service;

import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.repository.UtxoRepository;
import com.easy1staking.jpgstore.sniper.contract.MerkleTreeSnipeContract;
import com.easy1staking.jpgstore.sniper.contract.PolicyIdSnipeContract;
import com.easy1staking.jpgstore.sniper.model.MerkleSnipe;
import com.easy1staking.jpgstore.sniper.model.Snipe;
import com.easy1staking.jpgstore.sniper.model.entity.SnipeId;
import com.easy1staking.jpgstore.sniper.model.onchain.MerkleMintRedeemerParser;
import com.easy1staking.jpgstore.sniper.model.onchain.SnipeDatumParser;
import com.easy1staking.jpgstore.sniper.repository.MerkleSnipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class SnipeService {

    private final Network network;

    private final PolicyIdSnipeContract policyIdSnipeContract;

    private final MerkleTreeSnipeContract merkleTreeSnipeContract;

    private final UtxoRepository utxoRepository;

    private final MerkleSnipeRepository merkleSnipeRepository;

    private final SnipeDatumParser snipeDatumParser;

    private final MerkleMintRedeemerParser merkleMintRedeemerParser;

    private final SnipeRegistry snipeRegistry;

    public List<Snipe> findAllSnipes() {
        var policySnipes = utxoRepository.findUnspentByOwnerPaymentCredential(policyIdSnipeContract.getScriptHash(), Pageable.unpaged())
                .stream()
                .flatMap(Collection::stream)
                .flatMap(utxoEntity -> snipeDatumParser.parse(utxoEntity.getInlineDatum())
                        .map(snipeDatum -> new Snipe(utxoEntity.getTxHash(),
                                utxoEntity.getOutputIndex(),
                                snipeDatum.ownerPkh(),
                                snipeDatum.nftDestination().toAddress(network).getAddress(),
                                snipeDatum.targetHash(),
                                snipeDatum.maxPrice(),
                                snipeDatum.protocolFee()
                        )).stream());

        var merkleSnipes = utxoRepository.findUnspentByOwnerPaymentCredential(merkleTreeSnipeContract.getScriptHash(), Pageable.unpaged())
                .stream()
                .flatMap(Collection::stream)
                .flatMap(utxoEntity -> merkleSnipeRepository.findById(SnipeId.builder()
                                .txHash(utxoEntity.getTxHash())
                                .outputIndex(utxoEntity.getOutputIndex())
                                .build())
                        .stream()
                        .flatMap(merkleSnipe -> snipeDatumParser.parse(utxoEntity.getInlineDatum())
                                .map(snipeDatum -> new MerkleSnipe(utxoEntity.getTxHash(),
                                        utxoEntity.getOutputIndex(),
                                        snipeDatum.ownerPkh(),
                                        snipeDatum.nftDestination().toAddress(network).getAddress(),
                                        Arrays.asList(merkleSnipe.getNftList().split(",")),
                                        snipeDatum.targetHash(),
                                        snipeDatum.maxPrice(),
                                        snipeDatum.protocolFee()
                                )).stream())
                );

        return Stream.concat(policySnipes, merkleSnipes).toList();

    }


}
