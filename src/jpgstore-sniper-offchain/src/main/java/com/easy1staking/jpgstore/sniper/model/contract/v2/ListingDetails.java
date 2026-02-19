package com.easy1staking.jpgstore.sniper.model.contract.v2;

import java.util.List;

public record ListingDetails(String ownerPubKeyHash,
                             List<PaymentDetails> payees) {

}
