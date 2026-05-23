package com.auction.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TieResolution {
    private String lotId;
    private List<String> biddingTeamIds;
    private Long bidAmount;
    private boolean active;
}
