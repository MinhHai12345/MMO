package com.mmo.module.fb.service.impl;

import com.mmo.module.fb.entity.MatchPrediction;
import com.mmo.module.fb.entity.enums.MatchPredictionStatus;
import com.mmo.module.fb.predict.service.PredictionEngineService;
import com.mmo.module.fb.repository.MatchPredictionRepository;
import com.mmo.module.fb.service.MatchPredictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MatchPredictionServiceImpl implements MatchPredictionService {
    private final MatchPredictionRepository matchPredictionRepository;
    private final PredictionEngineService predictionEngineService;

    public void processAllPendingMatches() {
        List<MatchPrediction> pendingMatches = matchPredictionRepository.findByStatus(MatchPredictionStatus.READY);
        if (pendingMatches.isEmpty()) {
            return;
        }
        for (MatchPrediction match : pendingMatches) {
            predictionEngineService.calculateMatchPredict((match));
        }
        matchPredictionRepository.saveAll(pendingMatches);
    }
}
