package com.easy1staking.jpgstore.sniper.repository;

import com.easy1staking.jpgstore.sniper.model.entity.NftTokenTrait;
import com.easy1staking.jpgstore.sniper.model.entity.NftTokenTraitId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NftTokenTraitRepository extends JpaRepository<NftTokenTrait, NftTokenTraitId> {

    List<NftTokenTrait> findByAssetId(String assetId);

    void deleteByAssetId(String assetId);
}
