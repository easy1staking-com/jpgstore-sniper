package com.easy1staking.jpgstore.sniper.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class JpgDatum {

    @JsonProperty
    private String ownerPkh;

}
