package com.easy1staking.jpgstore.sniper.storage;

import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.UtxoCache;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.UtxoStorageImpl;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.repository.TxInputRepository;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.repository.UtxoRepository;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

@Repository
@Slf4j
public class CustomUtxoStorage extends UtxoStorageImpl {

    private final List<String> pubKeyHashes;

    public CustomUtxoStorage(UtxoRepository utxoRepository,
                             TxInputRepository spentOutputRepository,
                             DSLContext dsl,
                             UtxoCache utxoCache,
                             PlatformTransactionManager transactionManager,
                             List<String> pubKeyHashes) {
        super(utxoRepository, spentOutputRepository, dsl, utxoCache, transactionManager);
        this.pubKeyHashes = pubKeyHashes;
    }

    @Override
    public void saveUnspent(List<AddressUtxo> addressUtxoList) {
        var utxosToSave = addressUtxoList.stream()
                .filter(addressUtxo -> pubKeyHashes.contains(addressUtxo.getOwnerPaymentCredential()))
                .toList();
        super.saveUnspent(utxosToSave);
    }

}
