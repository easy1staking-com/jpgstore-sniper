package com.easy1staking.jpgstore.sniper.model.contract.v2;

import com.bloxbean.cardano.client.transaction.spec.Value;

import java.util.List;

public record ListingDetails(String ownerPubKeyHash,
                             List<PaymentDetails> payees) {

    public Value totalAmount() {
        return payees()
                .stream()
                .map(PaymentDetails::amount)
                .reduce(Value::add)
                .orElse(Value.builder().build());
    }

}
