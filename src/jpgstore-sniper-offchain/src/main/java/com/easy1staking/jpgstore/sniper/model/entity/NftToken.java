package com.easy1staking.jpgstore.sniper.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "nft_token")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NftToken {

    @Id
    @Column(name = "asset_id", length = 120)
    private String assetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private NftCollection collection;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "image", columnDefinition = "TEXT")
    private String image;

    @Column(name = "optimized_source", columnDefinition = "TEXT")
    private String optimizedSource;

    @Column(name = "mediatype", length = 100)
    private String mediatype;
}
