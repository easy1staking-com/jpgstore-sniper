package com.easy1staking.jpgstore.sniper.service;

import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.yaci.store.utxo.storage.impl.repository.UtxoRepository;
import com.easy1staking.jpgstore.sniper.contract.SettingsContract;
import com.easy1staking.jpgstore.sniper.model.onchain.Settings;
import com.easy1staking.jpgstore.sniper.model.onchain.SettingsParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsService {

    private final Network network;
    private final SettingsContract settingsContract;
    private final UtxoRepository utxoRepository;
    private final SettingsParser settingsParser;

    public Optional<SettingsDto> getSettings() {
        var settingsUtxoOpt = utxoRepository.findUnspentByOwnerPaymentCredential(
                        settingsContract.getScriptHash(), Pageable.unpaged())
                .stream()
                .flatMap(Collection::stream)
                .findAny();

        if (settingsUtxoOpt.isEmpty()) {
            log.warn("Could not find settings UTxO at script hash: {}", settingsContract.getScriptHash());
            return Optional.empty();
        }

        var settingsUtxo = settingsUtxoOpt.get();
        var settingsOpt = settingsParser.parse(settingsUtxo.getInlineDatum());

        if (settingsOpt.isEmpty()) {
            log.warn("Could not parse settings datum from UTxO: {}#{}", settingsUtxo.getTxHash(), settingsUtxo.getOutputIndex());
            return Optional.empty();
        }

        var settings = settingsOpt.get();
        var treasury = settings.protocolTreasury();
        var treasuryAddress = AddressProvider.getBaseAddress(
                treasury.paymentKeyHash(), treasury.stakeKeyHash(), network);

        return Optional.of(new SettingsDto(
                settings.operatorFeePct(),
                settings.protocolFeePct(),
                settings.minOperatorFee(),
                settings.minProtocolFee(),
                settings.txFeeBudget(),
                treasuryAddress.getAddress()
        ));
    }

    public record SettingsDto(
            int operatorFeePct,
            int protocolFeePct,
            long minOperatorFee,
            long minProtocolFee,
            long txFeeBudget,
            String protocolTreasury
    ) {}
}
