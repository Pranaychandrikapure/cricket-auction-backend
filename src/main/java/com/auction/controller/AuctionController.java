package com.auction.controller;

import com.auction.model.*;
import com.auction.repository.*;
import com.auction.websocket.AuctionWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api/auction")
@Slf4j
public class AuctionController {

    private final AuctionLotRepository lotRepository;
    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final BidRepository bidRepository;
    private final AuctionEventRepository eventRepository;
    private final AuctionRulesRepository rulesRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuctionWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    // Scheduler for backend-driven timers
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> timerTask = null;
    private int timeRemaining = 30;
    private String activeLotId = null;

    // Tie breaker in-memory tracker
    private TieResolution currentTie = null;

    // In-memory global auction status: "idle", "running", "paused", "completed"
    private String globalStatus = "idle";
    private int currentLotIndex = 0;

    public AuctionController(AuctionLotRepository lotRepository, PlayerRepository playerRepository,
                             TeamRepository teamRepository, BidRepository bidRepository,
                             AuctionEventRepository eventRepository, AuctionRulesRepository rulesRepository,
                             AuditLogRepository auditLogRepository, AuctionWebSocketHandler webSocketHandler,
                             ObjectMapper objectMapper) {
        this.lotRepository = lotRepository;
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
        this.bidRepository = bidRepository;
        this.eventRepository = eventRepository;
        this.rulesRepository = rulesRepository;
        this.auditLogRepository = auditLogRepository;
        this.webSocketHandler = webSocketHandler;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/status")
    public ResponseEntity<?> getAuctionStatus() {
        return ResponseEntity.ok(Map.of(
                "status", globalStatus,
                "currentLotIndex", currentLotIndex,
                "timeRemaining", timeRemaining,
                "activeLotId", activeLotId != null ? activeLotId : "",
                "tie", currentTie != null && currentTie.isActive() ? currentTie : Map.of("active", false)
        ));
    }

    @PostMapping("/status")
    public synchronized ResponseEntity<?> updateAuctionStatus(@RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status != null) {
            String oldStatus = globalStatus;
            globalStatus = status;
            log.info("Auction status updated from {} to {}", oldStatus, globalStatus);

            // Log action in AuditLog
            auditLogRepository.save(AuditLog.builder()
                    .actionType("AUCTION_" + status.toUpperCase())
                    .performedBy("Admin")
                    .details("Auction status changed from " + oldStatus + " to " + globalStatus)
                    .timestamp(Instant.now())
                    .build());

            // Handle timer scheduler states
            if ("running".equals(globalStatus)) {
                // If starting/resuming and we have an active lot, start or resume timer
                List<AuctionLot> lots = lotRepository.findAll();
                if (currentLotIndex < lots.size()) {
                    AuctionLot lot = lots.get(currentLotIndex);
                    // Make lot active if it was pending
                    if ("pending".equals(lot.getStatus())) {
                        lot.setStatus("active");
                        lotRepository.save(lot);
                        notifyClients("lot_updated", lot);
                    }
                    
                    AuctionRules rules = rulesRepository.findById("rules-default")
                            .orElse(AuctionRules.builder().timerDuration(30).build());
                    startTimer(lot.getId(), rules.getTimerDuration());
                }
            } else if ("paused".equals(globalStatus) || "completed".equals(globalStatus)) {
                pauseTimer();
            }

            notifyClients("status_updated", Map.of(
                    "status", globalStatus,
                    "currentLotIndex", currentLotIndex,
                    "timeRemaining", timeRemaining
            ));
            return ResponseEntity.ok(Map.of("status", globalStatus));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Status field is required"));
    }

    @GetMapping("/lots")
    public List<AuctionLot> getAllLots() {
        return lotRepository.findAll();
    }

    @GetMapping("/events")
    public List<AuctionEvent> getEvents() {
        return eventRepository.findTop10ByOrderByCreatedAtDesc();
    }

    @GetMapping("/logs")
    public List<AuditLog> getAuditLogs() {
        return auditLogRepository.findTop50ByOrderByTimestampDesc();
    }

    @GetMapping("/rules")
    public ResponseEntity<AuctionRules> getRules() {
        AuctionRules rules = rulesRepository.findById("rules-default")
                .orElseGet(() -> rulesRepository.save(AuctionRules.builder()
                        .id("rules-default")
                        .totalPurse(1500000L)
                        .maxPlayers(9)
                        .minPlayers(5)
                        .minBidIncrement(50000L)
                        .minPlayerCost(50000L)
                        .randomizedOrder(false)
                        .timerDuration(30)
                        .build()));
        return ResponseEntity.ok(rules);
    }

    @PutMapping("/rules")
    public ResponseEntity<?> updateRules(@RequestBody AuctionRules newRules) {
        AuctionRules rules = rulesRepository.findById("rules-default").orElse(new AuctionRules());
        rules.setId("rules-default");
        
        if (newRules.getTotalPurse() != null) rules.setTotalPurse(newRules.getTotalPurse());
        if (newRules.getMaxPlayers() != null) rules.setMaxPlayers(newRules.getMaxPlayers());
        if (newRules.getMinPlayers() != null) rules.setMinPlayers(newRules.getMinPlayers());
        if (newRules.getMinBidIncrement() != null) rules.setMinBidIncrement(newRules.getMinBidIncrement());
        if (newRules.getMinPlayerCost() != null) rules.setMinPlayerCost(newRules.getMinPlayerCost());
        if (newRules.getRandomizedOrder() != null) rules.setRandomizedOrder(newRules.getRandomizedOrder());
        if (newRules.getTimerDuration() != null) rules.setTimerDuration(newRules.getTimerDuration());

        AuctionRules saved = rulesRepository.save(rules);

        // Audit Log
        auditLogRepository.save(AuditLog.builder()
                .actionType("RULE_UPDATE")
                .performedBy("Admin")
                .details("Updated auction rules: Min Players=" + rules.getMinPlayers() + ", Purse=" + rules.getTotalPurse())
                .timestamp(Instant.now())
                .build());

        notifyClients("rules_updated", saved);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/lots/sell")
    public synchronized ResponseEntity<?> sellPlayer(@RequestBody SellRequest request) {
        return executeSell(request.getLotId(), request.getTeamId());
    }

    private synchronized ResponseEntity<?> executeSell(String lotId, String teamId) {
        Optional<AuctionLot> optionalLot = lotRepository.findById(lotId);
        if (optionalLot.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Lot not found"));
        }

        AuctionLot lot = optionalLot.get();
        Optional<Team> optionalTeam = teamRepository.findById(teamId);
        if (optionalTeam.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Team not found"));
        }

        Team team = optionalTeam.get();
        Optional<Player> optionalPlayer = playerRepository.findById(lot.getPlayerId());
        if (optionalPlayer.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Player not found"));
        }
        Player player = optionalPlayer.get();

        // Clear active timer if running
        pauseTimer();

        // Update lot status
        lot.setStatus("sold");
        lot.setSoldToTeamId(team.getId());
        lot.setSoldAt(Instant.now());
        lotRepository.save(lot);

        // Update team stats
        team.setRemainingPurse(team.getRemainingPurse() - lot.getCurrentBid());
        team.setPurchasedPlayersCount(team.getPurchasedPlayersCount() + 1);
        teamRepository.save(team);

        // Save event
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("player_name", player.getName());
        eventData.put("team_name", team.getTeamName());
        eventData.put("price", lot.getCurrentBid());

        AuctionEvent event = AuctionEvent.builder()
                .eventType("player_sold")
                .lotId(lot.getId())
                .teamId(team.getId())
                .playerId(player.getId())
                .data(eventData)
                .createdAt(Instant.now())
                .build();
        eventRepository.save(event);

        // Log to Audit Log
        auditLogRepository.save(AuditLog.builder()
                .actionType("PLAYER_SOLD")
                .performedBy("Admin")
                .details(player.getName() + " sold to " + team.getTeamName() + " for ₹" + lot.getCurrentBid())
                .timestamp(Instant.now())
                .build());

        // Increment index & advance
        advanceLotIndex();

        notifyClients("player_sold", Map.of(
                "lot", lot,
                "team", team,
                "event", event,
                "currentLotIndex", currentLotIndex
        ));

        // Start next lot timer if running
        autoStartNextLot();

        return ResponseEntity.ok(Map.of("success", true, "lot", lot, "team", team));
    }

    @PostMapping("/lots/unsold")
    public synchronized ResponseEntity<?> markUnsold(@RequestBody Map<String, String> body) {
        String lotId = body.get("lotId");
        return executeUnsold(lotId);
    }

    private synchronized ResponseEntity<?> executeUnsold(String lotId) {
        Optional<AuctionLot> optionalLot = lotRepository.findById(lotId);
        if (optionalLot.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Lot not found"));
        }

        AuctionLot lot = optionalLot.get();
        pauseTimer();

        lot.setStatus("unsold");
        lotRepository.save(lot);

        AuctionEvent event = AuctionEvent.builder()
                .eventType("player_unsold")
                .lotId(lot.getId())
                .playerId(lot.getPlayerId())
                .createdAt(Instant.now())
                .build();
        eventRepository.save(event);

        // Audit Log
        auditLogRepository.save(AuditLog.builder()
                .actionType("PLAYER_UNSOLD")
                .performedBy("Admin")
                .details("Player marked unsold for lot " + lot.getId())
                .timestamp(Instant.now())
                .build());

        advanceLotIndex();

        notifyClients("player_unsold", Map.of(
                "lot", lot,
                "event", event,
                "currentLotIndex", currentLotIndex
        ));

        autoStartNextLot();

        return ResponseEntity.ok(Map.of("success", true, "lot", lot));
    }

    @PostMapping("/bids")
    public synchronized ResponseEntity<?> placeBid(@RequestBody BidRequest request) {
        Optional<AuctionLot> optionalLot = lotRepository.findById(request.getLotId());
        if (optionalLot.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Lot not found"));
        }

        AuctionLot lot = optionalLot.get();
        if ("sold".equals(lot.getStatus()) || "unsold".equals(lot.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Lot is already completed"));
        }

        Optional<Team> optionalTeam = teamRepository.findById(request.getTeamId());
        if (optionalTeam.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Team not found"));
        }

        Team team = optionalTeam.get();
        AuctionRules rules = rulesRepository.findById("rules-default")
                .orElse(AuctionRules.builder()
                        .minPlayers(5)
                        .minPlayerCost(50000L)
                        .timerDuration(30)
                        .build());

        // Validate bid increments
        long minimumBid = lot.getCurrentBid() + (lot.getCurrentBidderTeamId() == null ? 0 : rules.getMinBidIncrement());
        if (request.getAmount() < minimumBid) {
            return ResponseEntity.badRequest().body(Map.of("error", "Bid must be at least ₹" + minimumBid));
        }

        // Smart Purse Check: Remaining purse after this bid must be enough to buy remaining players to reach the minimum required squad size
        int currentCount = team.getPurchasedPlayersCount();
        int remainingRequired = rules.getMinPlayers() - currentCount;
        
        // If we haven't reached the minimum squad size, calculate buffer
        if (remainingRequired > 0) {
            // We need 'remainingRequired - 1' more players after this bid.
            long requiredBuffer = (remainingRequired - 1) * rules.getMinPlayerCost();
            long potentialRemainingPurse = team.getRemainingPurse() - request.getAmount();

            if (potentialRemainingPurse < requiredBuffer) {
                return ResponseEntity.badRequest().body(Map.of("error", 
                        "Smart Purse Violation! Placing a bid of ₹" + request.getAmount() + 
                        " leaves ₹" + potentialRemainingPurse + " remaining. You need at least ₹" + 
                        requiredBuffer + " to purchase " + (remainingRequired - 1) + " more required players (minimum ₹" + 
                        rules.getMinPlayerCost() + " each)."));
            }
        }

        if (request.getAmount() > team.getRemainingPurse()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Insufficient remaining purse: " + team.getRemainingPurse()));
        }

        // Check for Tie: If two teams place bids of identical values at the same time
        if (lot.getCurrentBid().equals(request.getAmount()) && lot.getCurrentBidderTeamId() != null) {
            // Establish a tie condition
            currentTie = TieResolution.builder()
                    .lotId(lot.getId())
                    .biddingTeamIds(new ArrayList<>(List.of(lot.getCurrentBidderTeamId(), team.getId())))
                    .bidAmount(request.getAmount())
                    .active(true)
                    .build();

            notifyClients("tie_detected", currentTie);
            return ResponseEntity.ok(Map.of("tie", true, "message", "A bidding tie has occurred!", "tieDetails", currentTie));
        }

        // Place the bid
        Bid bid = Bid.builder()
                .lotId(lot.getId())
                .teamId(team.getId())
                .amount(request.getAmount())
                .bidTime(Instant.now())
                .build();
        bidRepository.save(bid);

        // Update lot details
        lot.setCurrentBid(request.getAmount());
        lot.setCurrentBidderTeamId(team.getId());
        lot.setStatus("active");
        lotRepository.save(lot);

        // Create log event
        AuctionEvent event = AuctionEvent.builder()
                .eventType("bid_placed")
                .lotId(lot.getId())
                .teamId(team.getId())
                .playerId(lot.getPlayerId())
                .data(Map.of(
                        "bid_amount", request.getAmount(),
                        "team_name", team.getTeamName()
                ))
                .createdAt(Instant.now())
                .build();
        eventRepository.save(event);

        // Reset server-side countdown timer on bid!
        resetTimer(rules.getTimerDuration());

        notifyClients("bid_placed", Map.of(
                "lot", lot,
                "team", team,
                "event", event
        ));

        return ResponseEntity.ok(Map.of("success", true, "lot", lot, "bid", bid));
    }

    // Tie Handling Endpoint: Resolve with Coin Toss
    @PostMapping("/ties/toss")
    public synchronized ResponseEntity<?> resolveTieWithToss() {
        if (currentTie == null || !currentTie.isActive()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No active tie to resolve"));
        }

        List<String> teamIds = currentTie.getBiddingTeamIds();
        Random rand = new Random();
        String winningTeamId = teamIds.get(rand.nextInt(teamIds.size()));
        
        Optional<Team> optionalTeam = teamRepository.findById(winningTeamId);
        String winningTeamName = optionalTeam.map(Team::getTeamName).orElse(winningTeamId);

        // Audit Log
        auditLogRepository.save(AuditLog.builder()
                .actionType("TIE_RESOLVED")
                .performedBy("Admin")
                .details("Tie resolved via Coin Toss. Winner: " + winningTeamName)
                .timestamp(Instant.now())
                .build());

        currentTie.setActive(false);
        TieResolution resolved = currentTie;
        currentTie = null;

        notifyClients("tie_resolved", Map.of("winnerId", winningTeamId, "winnerName", winningTeamName));

        // Auto sell to winner
        return executeSell(resolved.getLotId(), winningTeamId);
    }

    // Tie Handling Endpoint: Withdraw from Tie
    @PostMapping("/ties/withdraw")
    public synchronized ResponseEntity<?> withdrawFromTie(@RequestBody Map<String, String> body) {
        String teamId = body.get("teamId");
        if (currentTie == null || !currentTie.isActive()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No active tie to withdraw from"));
        }

        List<String> teamIds = currentTie.getBiddingTeamIds();
        if (!teamIds.contains(teamId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Team is not part of this tie"));
        }

        teamIds.remove(teamId);
        
        // If only one team remains, they win automatically
        if (teamIds.size() == 1) {
            String winningTeamId = teamIds.get(0);
            Optional<Team> optionalTeam = teamRepository.findById(winningTeamId);
            String winningTeamName = optionalTeam.map(Team::getTeamName).orElse(winningTeamId);

            currentTie.setActive(false);
            TieResolution resolved = currentTie;
            currentTie = null;

            notifyClients("tie_resolved", Map.of("winnerId", winningTeamId, "winnerName", winningTeamName, "reason", "Withdrawal"));

            return executeSell(resolved.getLotId(), winningTeamId);
        }

        notifyClients("tie_updated", currentTie);
        return ResponseEntity.ok(Map.of("success", true, "tie", currentTie));
    }

    // Analytics Dashboard
    @GetMapping("/analytics")
    public ResponseEntity<?> getAnalytics() {
        List<AuctionLot> lots = lotRepository.findAll();
        List<Team> teams = teamRepository.findAll();

        long highestSoldPrice = 0L;
        String highestSoldPlayerName = "None";

        Map<String, Long> teamSpends = new HashMap<>();
        Map<String, Integer> teamCounts = new HashMap<>();

        for (Team t : teams) {
            teamSpends.put(t.getTeamName(), t.getTotalPurse() - t.getRemainingPurse());
            teamCounts.put(t.getTeamName(), t.getPurchasedPlayersCount());
        }

        for (AuctionLot lot : lots) {
            if ("sold".equals(lot.getStatus())) {
                if (lot.getCurrentBid() > highestSoldPrice) {
                    highestSoldPrice = lot.getCurrentBid();
                    Optional<Player> p = playerRepository.findById(lot.getPlayerId());
                    highestSoldPlayerName = p.map(Player::getName).orElse("Unknown");
                }
            }
        }

        return ResponseEntity.ok(Map.of(
                "highestSoldPlayer", highestSoldPlayerName,
                "highestSoldPrice", highestSoldPrice,
                "teamSpends", teamSpends,
                "teamSquadSizes", teamCounts
        ));
    }

    @PostMapping("/reset")
    public ResponseEntity<?> resetAuction() {
        log.info("Resetting auction system to initial state...");
        // Re-read base prices from players and reset all lots to pending
        List<Player> players = playerRepository.findAll();
        for (Player p : players) {
            Optional<AuctionLot> optionalLot = lotRepository.findById("lot-" + p.getId());
            AuctionLot lot = optionalLot.orElseGet(() -> new AuctionLot("lot-" + p.getId(), p.getId(), 0L, null, "pending", null, null));
            lot.setStatus("pending");
            lot.setCurrentBid(p.getBasePrice());
            lot.setCurrentBidderTeamId(null);
            lot.setSoldToTeamId(null);
            lot.setSoldAt(null);
            lotRepository.save(lot);
        }

        // Reset team purses
        List<Team> teams = teamRepository.findAll();
        for (Team t : teams) {
            t.setRemainingPurse(t.getTotalPurse());
            t.setMagicCardUsed(false);
            t.setPurchasedPlayersCount(0);
            teamRepository.save(t);
        }

        // Clear bids and events
        bidRepository.deleteAll();
        eventRepository.deleteAll();
        auditLogRepository.deleteAll();

        // Audit Log
        auditLogRepository.save(AuditLog.builder()
                .actionType("RESET")
                .performedBy("Admin")
                .details("Resetted auction status and databases.")
                .timestamp(Instant.now())
                .build());

        globalStatus = "idle";
        currentLotIndex = 0;
        pauseTimer();
        currentTie = null;

        notifyClients("auction_reset", Map.of("success", true));
        return ResponseEntity.ok(Map.of("success", true, "message", "Auction successfully reset."));
    }

    // Helper: Starts backend scheduler tick loop
    private synchronized void startTimer(String lotId, int duration) {
        if (timerTask != null) {
            timerTask.cancel(false);
        }
        activeLotId = lotId;
        timeRemaining = duration;

        timerTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                synchronized (this) {
                    if (!"running".equals(globalStatus)) {
                        return;
                    }
                    timeRemaining--;
                    double percentage = ((double) timeRemaining / duration) * 100;
                    notifyClients("timer_tick", Map.of(
                            "timeRemaining", timeRemaining,
                            "percentage", Math.max(0, percentage),
                            "lotId", activeLotId
                    ));

                    if (timeRemaining <= 0) {
                        if (timerTask != null) {
                            timerTask.cancel(false);
                            timerTask = null;
                        }
                        log.info("Timer hit zero for lot {}. Triggering resolution...", activeLotId);
                        // Resolve inside the same block
                        resolveActiveLot(activeLotId);
                    }
                }
            } catch (Exception e) {
                log.error("Error in scheduled timer tick: ", e);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    // Helper: Pauses the timer
    private synchronized void pauseTimer() {
        if (timerTask != null) {
            timerTask.cancel(false);
            timerTask = null;
        }
    }

    // Helper: Resets countdown time
    private synchronized void resetTimer(int duration) {
        timeRemaining = duration;
    }

    // Helper: Execute auto-sell or auto-unsold when timer hits zero
    private void resolveActiveLot(String lotId) {
        Optional<AuctionLot> optionalLot = lotRepository.findById(lotId);
        if (optionalLot.isPresent()) {
            AuctionLot lot = optionalLot.get();
            if ("active".equals(lot.getStatus()) || "pending".equals(lot.getStatus())) {
                if (lot.getCurrentBidderTeamId() != null) {
                    log.info("Auto-selling lot {} to team {}", lotId, lot.getCurrentBidderTeamId());
                    executeSell(lotId, lot.getCurrentBidderTeamId());
                } else {
                    log.info("Auto-marking lot {} as unsold", lotId);
                    executeUnsold(lotId);
                }
            }
        }
    }

    private void advanceLotIndex() {
        List<AuctionLot> allLots = lotRepository.findAll();
        if (currentLotIndex < allLots.size() - 1) {
            currentLotIndex++;
        } else {
            globalStatus = "completed";
            auditLogRepository.save(AuditLog.builder()
                    .actionType("AUCTION_COMPLETED")
                    .performedBy("System")
                    .details("All player lots complete.")
                    .timestamp(Instant.now())
                    .build());
        }
    }

    private void autoStartNextLot() {
        if ("running".equals(globalStatus)) {
            List<AuctionLot> lots = lotRepository.findAll();
            if (currentLotIndex < lots.size()) {
                AuctionLot nextLot = lots.get(currentLotIndex);
                if ("pending".equals(nextLot.getStatus())) {
                    nextLot.setStatus("active");
                    lotRepository.save(nextLot);
                    notifyClients("lot_updated", nextLot);
                }
                AuctionRules rules = rulesRepository.findById("rules-default")
                        .orElse(AuctionRules.builder().timerDuration(30).build());
                startTimer(nextLot.getId(), rules.getTimerDuration());
            }
        }
    }

    private void notifyClients(String type, Object data) {
        try {
            Map<String, Object> messageMap = new HashMap<>();
            messageMap.put("type", type);
            messageMap.put("data", data);
            String jsonPayload = objectMapper.writeValueAsString(messageMap);
            webSocketHandler.broadcast(jsonPayload);
        } catch (Exception e) {
            log.error("Failed to serialize and broadcast WebSocket event: ", e);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BidRequest {
        private String lotId;
        private String teamId;
        private Long amount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SellRequest {
        private String lotId;
        private String teamId;
    }
}
