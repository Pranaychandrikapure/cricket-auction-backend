package com.auction.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "auction_lots")
public class AuctionLot {
    @Id
    @Builder.Default
    private String id = java.util.UUID.randomUUID().toString();
    private String playerId;
    private Long currentBid;
    private String currentBidderTeamId;
    private String status; // "pending", "active", "sold", "unsold"
    private String soldToTeamId;
    private Instant soldAt;
}
