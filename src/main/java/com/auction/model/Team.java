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
@Table(name = "teams")
public class Team {
    @Id
    @Builder.Default
    private String id = java.util.UUID.randomUUID().toString();
    private String teamName;
    private String teamColor;
    private Long totalPurse;
    private Long remainingPurse;
    private Boolean magicCardUsed;
    @Builder.Default
    private Integer purchasedPlayersCount = 0;
}
