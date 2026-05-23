package com.auction.service;

import com.auction.model.AuctionLot;
import com.auction.model.AuctionRules;
import com.auction.model.Player;
import com.auction.model.Team;
import com.auction.repository.AuctionLotRepository;
import com.auction.repository.AuctionRulesRepository;
import com.auction.repository.PlayerRepository;
import com.auction.repository.TeamRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final AuctionLotRepository lotRepository;
    private final AuctionRulesRepository rulesRepository;

    public DataInitializer(PlayerRepository playerRepository, TeamRepository teamRepository,
                           AuctionLotRepository lotRepository, AuctionRulesRepository rulesRepository) {
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
        this.lotRepository = lotRepository;
        this.rulesRepository = rulesRepository;
    }

    @Override
    public void run(String... args) {
        // Seed default auction rules if empty
        if (rulesRepository.count() == 0) {
            log.info("Seeding default auction rules in Neon PostgreSQL...");
            AuctionRules defaultRules = AuctionRules.builder()
                    .id("rules-default")
                    .totalPurse(1500000L)
                    .maxPlayers(9)
                    .minPlayers(5)
                    .minBidIncrement(50000L)
                    .minPlayerCost(50000L)
                    .randomizedOrder(false)
                    .timerDuration(30)
                    .build();
            rulesRepository.save(defaultRules);
        }

        // Seed Teams if empty
        if (teamRepository.count() == 0) {
            log.info("Seeding teams data in Neon PostgreSQL...");
            List<Team> defaultTeams = List.of(
                    Team.builder().id("team-1").teamName("Team Red").teamColor("red").totalPurse(1500000L).remainingPurse(1500000L).magicCardUsed(false).purchasedPlayersCount(0).build(),
                    Team.builder().id("team-2").teamName("Team Blue").teamColor("blue").totalPurse(1500000L).remainingPurse(1500000L).magicCardUsed(false).purchasedPlayersCount(0).build(),
                    Team.builder().id("team-3").teamName("Team Green").teamColor("green").totalPurse(1500000L).remainingPurse(1500000L).magicCardUsed(false).purchasedPlayersCount(0).build(),
                    Team.builder().id("team-4").teamName("Team Purple").teamColor("purple").totalPurse(1500000L).remainingPurse(1500000L).magicCardUsed(false).purchasedPlayersCount(0).build()
            );
            teamRepository.saveAll(defaultTeams);
        }

        // Seed Players & Lots if empty
        if (playerRepository.count() == 0) {
            log.info("Seeding players data in Neon PostgreSQL...");
            List<Player> players = new ArrayList<>();
            players.add(Player.builder().id("p-1").name("Virat Kohli").role("Batsman").basePrice(1000000L).email("virat@auction.com").photoUrl("https://images.unsplash.com/photo-1531427186611-ecfd6d936c79?w=400&h=400&fit=crop").country("India").build());
            players.add(Player.builder().id("p-2").name("MS Dhoni").role("Wicket-keeper").basePrice(1000000L).email("dhoni@auction.com").photoUrl("https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400&h=400&fit=crop").country("India").build());
            players.add(Player.builder().id("p-3").name("Jasprit Bumrah").role("Bowler").basePrice(800000L).email("bumrah@auction.com").photoUrl("https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400&h=400&fit=crop").country("India").build());
            players.add(Player.builder().id("p-4").name("Ben Stokes").role("All-rounder").basePrice(900000L).email("stokes@auction.com").photoUrl("https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=400&h=400&fit=crop").country("England").build());
            players.add(Player.builder().id("p-5").name("Rashid Khan").role("Bowler").basePrice(750000L).email("rashid@auction.com").photoUrl("https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=400&h=400&fit=crop").country("Afghanistan").build());
            players.add(Player.builder().id("p-6").name("Steve Smith").role("Batsman").basePrice(800000L).email("smith@auction.com").photoUrl("https://images.unsplash.com/photo-1519345182560-3f2917c472ef?w=400&h=400&fit=crop").country("Australia").build());
            players.add(Player.builder().id("p-7").name("Jos Buttler").role("Wicket-keeper").basePrice(850000L).email("buttler@auction.com").photoUrl("https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=400&h=400&fit=crop").country("England").build());
            players.add(Player.builder().id("p-8").name("Glenn Maxwell").role("All-rounder").basePrice(700000L).email("maxwell@auction.com").photoUrl("https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=400&h=400&fit=crop").country("Australia").build());

            playerRepository.saveAll(players);

            log.info("Creating auction lots for seeded players...");
            List<AuctionLot> lots = new ArrayList<>();
            for (Player player : players) {
                lots.add(AuctionLot.builder()
                        .id("lot-" + player.getId())
                        .playerId(player.getId())
                        .currentBid(player.getBasePrice())
                        .currentBidderTeamId(null)
                        .status("pending")
                        .build());
            }
            lotRepository.saveAll(lots);
        }
    }
}
