package com.mmo.module.fb.controller;

import com.mmo.module.fb.domain.entity.Match;
import com.mmo.module.fb.domain.entity.MatchPrediction;
import com.mmo.module.fb.domain.repository.MatchPredictionRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
public class PredictionController {

    private final MatchPredictionRepository matchPredictionRepository;
    private final MatchRepository matchRepository;

    @GetMapping("/upcoming")
    public ResponseEntity<List<MatchPrediction>> getUpcomingPredictions(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            Instant now = Instant.now();
            Instant endTime = now.plus(48, java.time.temporal.ChronoUnit.HOURS);
            
            List<Match> upcomingMatches = matchRepository.findByMatchTimeBetweenAndStatus(
                    now, endTime, "SCHEDULED");
            
            List<MatchPrediction> predictions = upcomingMatches.stream()
                    .limit(limit)
                    .map(match -> matchPredictionRepository.findByMatchId(match.getId()).orElse(null))
                    .filter(prediction -> prediction != null && "PENDING".equals(prediction.getPredictionStatus()))
                    .toList();
            
            return ResponseEntity.ok(predictions);
        } catch (Exception e) {
            log.error("❌ Error fetching upcoming predictions: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/match/{matchId}")
    public ResponseEntity<MatchPrediction> getPredictionByMatchId(@PathVariable Long matchId) {
        try {
            Optional<MatchPrediction> prediction = matchPredictionRepository.findByMatchId(matchId);
            return prediction.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("❌ Error fetching prediction for match {}: {}", matchId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/premium")
    public ResponseEntity<List<MatchPrediction>> getPremiumPredictions(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<MatchPrediction> predictions = matchPredictionRepository.findByIsPremiumTrue()
                    .stream()
                    .filter(p -> "PENDING".equals(p.getPredictionStatus()))
                    .limit(limit)
                    .toList();
            
            return ResponseEntity.ok(predictions);
        } catch (Exception e) {
            log.error("❌ Error fetching premium predictions: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/date/{date}")
    public ResponseEntity<List<MatchPrediction>> getPredictionsByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) String date) {
        try {
            Instant startOfDay = Instant.parse(date + "T00:00:00Z");
            Instant endOfDay = startOfDay.plus(1, java.time.temporal.ChronoUnit.DAYS);
            
            List<Match> matches = matchRepository.findByMatchTimeBetweenAndStatus(
                    startOfDay, endOfDay, "SCHEDULED");
            
            List<MatchPrediction> predictions = matches.stream()
                    .map(match -> matchPredictionRepository.findByMatchId(match.getId()).orElse(null))
                    .filter(prediction -> prediction != null)
                    .toList();
            
            return ResponseEntity.ok(predictions);
        } catch (Exception e) {
            log.error("❌ Error fetching predictions for date {}: {}", date, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
