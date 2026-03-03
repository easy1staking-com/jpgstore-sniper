package com.easy1staking.jpgstore.sniper.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NftTokenTraitId implements Serializable {
    private String assetId;
    private String category;
}
