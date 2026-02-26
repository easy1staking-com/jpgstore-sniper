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
@Table(name = "merkle_snipe")
@IdClass(SnipeId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerkleSnipe {

    @Id
    @Column(name = "tx_hash", length = 64, nullable = false)
    private String txHash;

    @Id
    @Column(name = "output_index", nullable = false)
    private Integer outputIndex;

    @Column(name = "slot", nullable = false)
    private Long slot;

    @Column(name = "nft_list", columnDefinition = "TEXT", nullable = false)
    private String nftList;
}
