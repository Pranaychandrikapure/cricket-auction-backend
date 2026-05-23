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
@Table(name = "bids")
public class Bid {
    @Id
    @Builder.Default
    private String id = java.util.UUID.randomUUID().toString();
    private String lotId;
    private String teamId;
    private Long amount;
    private Instant bidTime;
}
