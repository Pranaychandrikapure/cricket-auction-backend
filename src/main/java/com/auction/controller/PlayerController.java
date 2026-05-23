package com.auction.controller;

import com.auction.model.AuctionLot;
import com.auction.model.Player;
import com.auction.repository.AuctionLotRepository;
import com.auction.repository.PlayerRepository;
import com.auction.websocket.AuctionWebSocketHandler;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerRepository playerRepository;
    private final AuctionLotRepository lotRepository;
    private final AuctionWebSocketHandler webSocketHandler;

    public PlayerController(PlayerRepository playerRepository, AuctionLotRepository lotRepository, AuctionWebSocketHandler webSocketHandler) {
        this.playerRepository = playerRepository;
        this.lotRepository = lotRepository;
        this.webSocketHandler = webSocketHandler;
    }

    @GetMapping
    public List<Player> getAllPlayers() {
        return playerRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    @PostMapping
    public Player createPlayer(@RequestBody Player player) {
        Player saved = playerRepository.save(player);

        // Automatically create an auction lot for this new player
        AuctionLot lot = AuctionLot.builder()
                .id("lot-" + saved.getId())
                .playerId(saved.getId())
                .currentBid(saved.getBasePrice())
                .status("pending")
                .build();
        lotRepository.save(lot);

        // Notify client side lists
        webSocketHandler.broadcast("{\"type\":\"players_updated\"}");
        return saved;
    }

    @PutMapping("/{id}")
    public ResponseEntity<Player> updatePlayer(@PathVariable String id, @RequestBody Player playerDetails) {
        Optional<Player> optionalPlayer = playerRepository.findById(id);
        if (optionalPlayer.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Player player = optionalPlayer.get();
        player.setName(playerDetails.getName());
        player.setRole(playerDetails.getRole());
        player.setBasePrice(playerDetails.getBasePrice());
        player.setEmail(playerDetails.getEmail());
        player.setPhotoUrl(playerDetails.getPhotoUrl());
        player.setCountry(playerDetails.getCountry());

        Player updated = playerRepository.save(player);

        // Update corresponding auction lot base price if it's still pending
        Optional<AuctionLot> optionalLot = lotRepository.findById("lot-" + id);
        if (optionalLot.isPresent()) {
            AuctionLot lot = optionalLot.get();
            if ("pending".equals(lot.getStatus())) {
                lot.setCurrentBid(updated.getBasePrice());
                lotRepository.save(lot);
            }
        }

        webSocketHandler.broadcast("{\"type\":\"players_updated\"}");
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePlayer(@PathVariable String id) {
        if (!playerRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        playerRepository.deleteById(id);
        lotRepository.deleteById("lot-" + id);

        webSocketHandler.broadcast("{\"type\":\"players_updated\"}");
        return ResponseEntity.ok().build();
    }
}
