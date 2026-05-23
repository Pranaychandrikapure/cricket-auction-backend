package com.auction.repository;

import com.auction.model.AuctionLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuctionLotRepository extends JpaRepository<AuctionLot, String> {
    List<AuctionLot> findByStatus(String status);
    Optional<AuctionLot> findFirstByStatusOrderByStatus(String status);
}
