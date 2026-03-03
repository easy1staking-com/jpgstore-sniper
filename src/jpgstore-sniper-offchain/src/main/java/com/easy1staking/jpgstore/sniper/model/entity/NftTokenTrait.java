package com.easy1staking.jpgstore.sniper.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "nft_token_trait")
@IdClass(NftTokenTraitId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NftTokenTrait {

    @Id
    @Column(name = "asset_id", length = 120, nullable = false)
    private String assetId;

    @Id
    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "value", nullable = false)
    private String value;
}
