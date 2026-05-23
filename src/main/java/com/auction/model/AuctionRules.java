package com.auction.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "auction_rules")
public class AuctionRules {
    @Id
    @Builder.Default
    private String id = java.util.UUID.randomUUID().toString();
    private Long totalPurse;       // e.g. 1500000
    private Integer maxPlayers;     // e.g. 9
    private Integer minPlayers;     // e.g. 5
    private Long minBidIncrement;  // e.g. 50000
    private Long minPlayerCost;    // e.g. 50000
    private Boolean randomizedOrder;
    private Integer timerDuration;  // in seconds, e.g. 30
}
