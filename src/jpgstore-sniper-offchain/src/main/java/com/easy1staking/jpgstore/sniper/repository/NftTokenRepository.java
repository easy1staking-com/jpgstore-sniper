package com.easy1staking.jpgstore.sniper.repository;

import com.easy1staking.jpgstore.sniper.model.entity.NftToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NftTokenRepository extends JpaRepository<NftToken, String> {
}
