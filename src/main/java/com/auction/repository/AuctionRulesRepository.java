package com.auction.repository;

import com.auction.model.AuctionRules;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuctionRulesRepository extends JpaRepository<AuctionRules, String> {
}
