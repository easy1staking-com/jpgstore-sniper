package com.easy1staking.jpgstore.sniper.repository;

import com.easy1staking.jpgstore.sniper.model.entity.CollectionTrait;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollectionTraitRepository extends JpaRepository<CollectionTrait, Long> {

    List<CollectionTrait> findByPolicyId(String policyId);

    List<CollectionTrait> findByPolicyIdAndCategory(String policyId, String category);

    void deleteByPolicyId(String policyId);
}
