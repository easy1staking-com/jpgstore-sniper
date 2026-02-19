package com.easy1staking.jpgstore.sniper.storage;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.store.common.domain.AddressUtxo;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.UtxoCache;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.UtxoStorageImpl;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.repository.TxInputRepository;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.repository.UtxoRepository;
import com.easy1staking.jpgstore.sniper.model.Constants;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.stream.Stream;

@Repository
@Slf4j
public class CustomUtxoStorage extends UtxoStorageImpl {

    private List<String> relevantScriptHashes;

    @PostConstruct
    public void init() {
        log.info("INIT");

        var v1Address = new Address(Constants.JPG_CONTRACT_ADDRESS_V1);
        var v2Address = new Address(Constants.JPG_CONTRACT_ADDRESS_V2);

        relevantScriptHashes = Stream.of(v1Address, v2Address)
                .flatMap(address -> address.getPaymentCredentialHash().map(HexUtil::encodeHexString).stream())
                .toList();

    }

    public CustomUtxoStorage(UtxoRepository utxoRepository,
                             TxInputRepository spentOutputRepository,
                             DSLContext dsl,
                             UtxoCache utxoCache,
                             PlatformTransactionManager transactionManager) {
        super(utxoRepository, spentOutputRepository, dsl, utxoCache, transactionManager);
    }

    @Override
    public void saveUnspent(List<AddressUtxo> addressUtxoList) {
        var utxosToSave = addressUtxoList.stream()
                .filter(addressUtxo -> relevantScriptHashes.contains(addressUtxo.getOwnerPaymentCredential()))
                .toList();
        super.saveUnspent(utxosToSave);
    }

}
