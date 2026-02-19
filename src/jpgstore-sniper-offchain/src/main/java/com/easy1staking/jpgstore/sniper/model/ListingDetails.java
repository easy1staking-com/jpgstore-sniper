package com.easy1staking.jpgstore.sniper.model;

import java.util.Optional;

public record ListingDetails(PaymentDetails jpgStoreFees, Optional<PaymentDetails> royalties, PaymentDetails seller) {

    public Long totalPrice() {
        return jpgStoreFees.lovelaces() + seller.lovelaces() + royalties.map(PaymentDetails::lovelaces).orElse(0L);
    }

}
