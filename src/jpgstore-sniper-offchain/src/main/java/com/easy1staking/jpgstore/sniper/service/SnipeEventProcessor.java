package com.easy1staking.jpgstore.sniper.service;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.core.model.RedeemerTag;
import com.bloxbean.cardano.yaci.core.model.TransactionInput;
import com.bloxbean.cardano.yaci.store.events.TransactionEvent;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.repository.UtxoRepository;
import com.easy1staking.jpgstore.sniper.contract.MerkleTreeSnipeContract;
import com.easy1staking.jpgstore.sniper.contract.PolicyIdSnipeContract;
import com.easy1staking.jpgstore.sniper.contract.SettingsContract;
import com.easy1staking.jpgstore.sniper.model.entity.MerkleSnipe;
import com.easy1staking.jpgstore.sniper.model.entity.SnipeId;
import com.easy1staking.jpgstore.sniper.model.onchain.MerkleMintRedeemerParser;
import com.easy1staking.jpgstore.sniper.model.onchain.SnipeDatumParser;
import com.easy1staking.jpgstore.sniper.repository.MerkleSnipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cardanofoundation.merkle.MerkleTree;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class SnipeEventProcessor {

    private final SettingsContract settingsContract;

    private final PolicyIdSnipeContract policyIdSnipeContract;

    private final MerkleTreeSnipeContract merkleTreeSnipeContract;

    private final UtxoRepository utxoRepository;

    private final MerkleSnipeRepository merkleSnipeRepository;

    private final SnipeDatumParser snipeDatumParser;

    private final MerkleMintRedeemerParser merkleMintRedeemerParser;

    private final SnipeRegistry snipeRegistry;

    @EventListener
    public void processTransactionEvent(TransactionEvent transactionEvent) {

        var slot = transactionEvent.getMetadata().getSlot();

        var relevantScriptHashes = List.of(policyIdSnipeContract.getScriptHash(), merkleTreeSnipeContract.getScriptHash());

        var anySnipeListed = transactionEvent.getTransactions()
                .stream()
                .reduce(false, (acc, transaction) -> {

                    var localAcc = acc;
                    for (int i = 0; i < transaction.getBody().getOutputs().size(); i++) {
                        var output = transaction.getBody().getOutputs().get(i);

                        var paymentPkhOpt = new Address(output.getAddress()).getPaymentCredentialHash().map(HexUtil::encodeHexString);
                        final var j = i;
                        paymentPkhOpt.filter(pkh -> pkh.equals(merkleTreeSnipeContract.getScriptHash()))
                                .ifPresent(pkh -> {

                                    var datumOpt = snipeDatumParser.parse(output.getInlineDatum());
                                    if (datumOpt.isEmpty()) {
                                        log.warn("counld not deserialise datum");
                                        return;
                                    }

                                    var datum = datumOpt.get();

                                    var redeemerOpt = transaction.getWitnesses()
                                            .getRedeemers()
                                            .stream()
                                            .filter(redeemer -> redeemer.getTag().equals(RedeemerTag.Mint))
                                            .flatMap(redeemer -> merkleMintRedeemerParser.parse(redeemer.getData().getCbor()).stream())
                                            .flatMap(mintRedeemer -> {
                                                var tree = MerkleTree.fromList(mintRedeemer.nfts(), HexUtil::decodeHexString);
                                                if (HexUtil.encodeHexString(tree.itemHash()).equals(datum.targetHash())) {
                                                    return Stream.of(mintRedeemer);
                                                } else {
                                                    return Stream.empty();
                                                }
                                            })
                                            .findAny();

                                    if (redeemerOpt.isEmpty()) {
                                        log.warn("could not resolve redeemer");
                                        return;
                                    }

                                    var redeemer = redeemerOpt.get();

                                    merkleSnipeRepository.save(MerkleSnipe.builder()
                                            .txHash(transaction.getTxHash())
                                            .outputIndex(j)
                                            .slot(slot)
                                            .nftList(String.join(",", redeemer.nfts()))
                                            .build());

                                });
                        localAcc = localAcc || paymentPkhOpt.map(relevantScriptHashes::contains).orElse(false);
                    }

                    return localAcc;
                }, (a, b) -> a || b);

        if (anySnipeListed) {
            log.info("at least a snipe listed!");
            snipeRegistry.clearAll();

            // Traverse For listing
            utxoRepository.findUnspentByOwnerPaymentCredential(policyIdSnipeContract.getScriptHash(), Pageable.unpaged())
                    .stream()
                    .flatMap(Collection::stream)
                    .forEach(utxoEntity -> {
                        snipeDatumParser.parse(utxoEntity.getInlineDatum())
                                .ifPresent(localDatum -> snipeRegistry.putPolicySnipe(localDatum.targetHash(), TransactionInput.builder()
                                        .transactionId(utxoEntity.getTxHash())
                                        .index(utxoEntity.getOutputIndex())
                                        .build()));
                    });

            utxoRepository.findUnspentByOwnerPaymentCredential(merkleTreeSnipeContract.getScriptHash(), Pageable.unpaged())
                    .stream()
                    .flatMap(Collection::stream)
                    .forEach(utxoEntity -> {
                        merkleSnipeRepository.findById(SnipeId.builder()
                                        .txHash(utxoEntity.getTxHash())
                                        .outputIndex(utxoEntity.getOutputIndex())
                                        .build())
                                .ifPresent(merkleSnipe -> {
                                    var nfts = Arrays.asList(merkleSnipe.getNftList().split(","));
                                    nfts.forEach(nft -> snipeRegistry.putMerkleSnipe(nft, TransactionInput.builder()
                                            .transactionId(utxoEntity.getTxHash())
                                            .index(utxoEntity.getOutputIndex())
                                            .build()));
                                });

                    });
        }
    }

}
