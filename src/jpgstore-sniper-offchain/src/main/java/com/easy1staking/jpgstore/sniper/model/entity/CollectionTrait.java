package com.easy1staking.jpgstore.sniper.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "collection_trait")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionTrait {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", length = 56, nullable = false)
    private String policyId;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "value", nullable = false)
    private String value;

    @Column(name = "nft_count", nullable = false)
    private int nftCount;
}
