package com.easy1staking.jpgstore.sniper.repository;

import com.easy1staking.jpgstore.sniper.model.entity.MerkleSnipe;
import com.easy1staking.jpgstore.sniper.model.entity.SnipeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MerkleSnipeRepository extends JpaRepository<MerkleSnipe, SnipeId> {

    void deleteBySlotGreaterThan(Long slot);
}
