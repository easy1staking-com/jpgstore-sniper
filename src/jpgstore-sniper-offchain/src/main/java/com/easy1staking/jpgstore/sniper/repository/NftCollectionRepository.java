package com.easy1staking.jpgstore.sniper.repository;

import com.easy1staking.jpgstore.sniper.model.entity.NftCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NftCollectionRepository extends JpaRepository<NftCollection, String> {
}
