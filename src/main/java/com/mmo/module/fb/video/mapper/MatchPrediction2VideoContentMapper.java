package com.mmo.module.fb.video.mapper;

import com.mmo.converter.AbstractMapper;
import com.mmo.module.fb.channel.model.PredictionData;
import com.mmo.module.fb.video.model.VideoContent;
import org.springframework.stereotype.Component;

@Component
public class MatchPrediction2VideoContentMapper extends AbstractMapper<PredictionData, VideoContent> {
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
