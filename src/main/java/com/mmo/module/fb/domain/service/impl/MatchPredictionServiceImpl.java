package com.mmo.module.fb.domain.service.impl;

import com.mmo.module.fb.domain.service.MatchPredictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MatchPredictionServiceImpl implements MatchPredictionService {
//    private final MatchPredictionRepository matchPredictionRepository;
//    private final PredictionEngineService predictionEngineService;

    public void processAllPendingMatches() {
//        List<MatchPrediction> pendingMatches = matchPredictionRepository.findByStatus(MatchPredictionStatus.READY);
//        if (pendingMatches.isEmpty()) {
//            return;
//        }
//        for (MatchPrediction match : pendingMatches) {
//            predictionEngineService.calculateMatchPredict((match));
//        }
//        matchPredictionRepository.saveAll(pendingMatches);
    }
}
