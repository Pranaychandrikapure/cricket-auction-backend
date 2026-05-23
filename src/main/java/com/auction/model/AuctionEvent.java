package com.auction.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "auction_events")
public class AuctionEvent {
    @Id
    @Builder.Default
    private String id = java.util.UUID.randomUUID().toString();
    private String eventType; // "bid_placed", "player_sold", "player_unsold", "magic_card_used", "email_sent"
    private String lotId;
    private String teamId;
    private String playerId;

    @Convert(converter = MapToJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> data;
    private Instant createdAt;
}
