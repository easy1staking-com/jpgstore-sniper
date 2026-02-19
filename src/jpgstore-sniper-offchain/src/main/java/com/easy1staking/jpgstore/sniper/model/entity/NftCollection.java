package com.easy1staking.jpgstore.sniper.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "nft_collection")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NftCollection {

    @Id
    @Column(name = "policy_id", length = 56)
    private String policyId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
