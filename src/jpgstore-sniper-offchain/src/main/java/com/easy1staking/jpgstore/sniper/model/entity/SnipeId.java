package com.easy1staking.jpgstore.sniper.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SnipeId implements Serializable {
    private String txHash;
    private Integer outputIndex;
}
