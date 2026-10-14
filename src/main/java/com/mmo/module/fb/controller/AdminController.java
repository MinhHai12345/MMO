package com.mmo.module.fb.controller;

import com.mmo.module.fb.domain.entity.League;
import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.domain.enums.MatchStatus;
import com.mmo.module.fb.domain.repository.LeagueRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import com.mmo.module.fb.domain.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;

    @GetMapping("/matches")
    public ResponseEntity<List<Match>> getAllMatches(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) String startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) String endDate) {
        try {
            List<Match> matches;
            
            if (status != null && startDate != null && endDate != null) {
                Instant start = Instant.parse(startDate);
                Instant end = Instant.parse(endDate);
                matches = matchRepository.findByMatchTimeBetweenAndStatus(
                        start, end, MatchStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT)));
            } else if (status != null) {
                matches = matchRepository.findByStatus(
                        MatchStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT)));
            } else {
                matches = matchRepository.findAll();
            }
            
            return ResponseEntity.ok(matches);
        } catch (Exception e) {
            log.error("❌ Error fetching matches: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/matches/{matchId}")
    public ResponseEntity<Match> getMatchById(@PathVariable Long matchId) {
        try {
            Optional<Match> match = matchRepository.findById(matchId);
            return match.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("❌ Error fetching match {}: {}", matchId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/teams")
    public ResponseEntity<List<Team>> getAllTeams(
            @RequestParam(required = false) String leagueId) {
        try {
            List<Team> teams;
            if (leagueId != null) {
                teams = teamRepository.findByLeagueId(Long.valueOf(leagueId));
            } else {
                teams = teamRepository.findAll();
            }
            return ResponseEntity.ok(teams);
        } catch (Exception e) {
            log.error("❌ Error fetching teams: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/leagues")
    public ResponseEntity<List<League>> getAllLeagues(
            @RequestParam(required = false) Boolean isActive) {
        try {
            List<League> leagues;
            if (isActive != null) {
                leagues = leagueRepository.findByIsActive(isActive);
            } else {
                leagues = leagueRepository.findAll();
            }
            return ResponseEntity.ok(leagues);
        } catch (Exception e) {
            log.error("❌ Error fetching leagues: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/matches/{matchId}/status")
    public ResponseEntity<Void> updateMatchStatus(
            @PathVariable Long matchId,
            @RequestBody Map<String, String> request) {
        try {
            Optional<Match> matchOpt = matchRepository.findById(matchId);
            if (matchOpt.isPresent()) {
                Match match = matchOpt.get();
                match.setStatus(MatchStatus.valueOf(request.get("status")));
                matchRepository.save(match);
                return ResponseEntity.ok().build();
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error updating match status: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
