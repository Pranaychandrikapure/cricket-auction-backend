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
@Table(name = "players")
public class Player {
    @Id
    @Builder.Default
    private String id = java.util.UUID.randomUUID().toString();
    private String name;
    private String role; // "Batsman", "Bowler", "All-rounder", "Wicket-keeper"
    private Long basePrice;
    private String photoUrl;
    private String email;
    private String country;
}
