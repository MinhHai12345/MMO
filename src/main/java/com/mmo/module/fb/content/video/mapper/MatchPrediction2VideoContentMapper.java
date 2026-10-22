package com.mmo.module.fb.content.video.mapper;

import com.mmo.core.converter.AbstractMapper;
import com.mmo.module.fb.content.video.model.VideoContent;
import com.mmo.module.fb.publish.model.PredictionData;
import org.springframework.stereotype.Component;

@Component
public class MatchPrediction2VideoContentMapper extends AbstractMapper<PredictionData, VideoContent> {

    @Override
    public VideoContent map(PredictionData source, VideoContent target) {
        if (source == null) {
            throw new IllegalArgumentException("Prediction data is required");
        }

        String home = textOrFallback(source.getHomeTeam(), "Home team");
        String away = textOrFallback(source.getAwayTeam(), "Away team");

        return VideoContent.builder()
                .hook("A football detail worth watching")
                .title(home + " vs " + away)
                .prediction(textOrFallback(source.getMostLikelyOutcome(),
                        "The available data does not support a stronger conclusion yet"))
                .score(source.getTopCorrectScores())
                .build();
    }

    private static String textOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
