package com.mmo.module.fb.content.video.mapper;

import com.mmo.module.fb.content.video.model.VideoContent;
import com.mmo.module.fb.publisher.model.PredictionData;
import org.springframework.stereotype.Component;

@Component
public class MatchPrediction2VideoContentMapper extends com.mmo.core.converter.AbstractMapper<PredictionData, VideoContent> {
    @Override
    public VideoContent map(PredictionData source, VideoContent target) {
        return VideoContent.builder()
                .hook("🚨 AI FOUND VALUE")
                .title(source.getHomeTeam() + " vs " + source.getAwayTeam())
                .prediction(source.getMostLikelyOutcome())
                .score(source.getTopCorrectScores())
                .build();
    }
}
