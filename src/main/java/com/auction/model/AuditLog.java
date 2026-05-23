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
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @Builder.Default
    private String id = java.util.UUID.randomUUID().toString();
    private String actionType;  // "RULE_UPDATE", "PURSE_UPDATE", "AUCTION_PAUSE", "TOSS_TRIGGERED", "RESET"
    private String performedBy;
    private String details;
    private Instant timestamp;
}
