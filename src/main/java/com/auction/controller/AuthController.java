package com.auction.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String password = request.getPassword();

        if ("admin@auction.com".equals(email)) {
            if ("admin123".equals(password) || "password123".equals(password)) {
                return ResponseEntity.ok(Map.of(
                        "user", Map.of(
                                "id", "admin-id",
                                "email", "admin@auction.com",
                                "role", "admin"
                        ),
                        "token", "admin-session-token-12345"
                ));
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid password for admin"));
            }
        }

        // Team accounts
        String teamId = null;
        String teamName = null;
        if ("team1@auction.com".equals(email)) {
            teamId = "team-1";
            teamName = "Team Red";
        } else if ("team2@auction.com".equals(email)) {
            teamId = "team-2";
            teamName = "Team Blue";
        } else if ("team3@auction.com".equals(email)) {
            teamId = "team-3";
            teamName = "Team Green";
        } else if ("team4@auction.com".equals(email)) {
            teamId = "team-4";
            teamName = "Team Purple";
        }

        if (teamId != null) {
            if ("password123".equals(password) || "admin123".equals(password)) {
                return ResponseEntity.ok(Map.of(
                        "user", Map.of(
                                "id", teamId,
                                "email", email,
                                "role", "team",
                                "teamName", teamName
                        ),
                        "token", "team-session-token-" + teamId
                ));
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid password for team representative"));
            }
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        private String email;
        private String password;
    }
}
