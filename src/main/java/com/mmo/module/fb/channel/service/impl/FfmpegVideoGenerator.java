package com.mmo.module.fb.channel.service.impl;

import com.mmo.module.fb.channel.model.PredictionData;
import com.mmo.module.fb.channel.service.VideoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
@RequiredArgsConstructor
public class FfmpegVideoGenerator implements VideoGenerator {
    @Override
    public File generate(PredictionData prediction) {
        String script = buildScript(prediction);

//        File voiceFile = voiceService.generateVoice(script);

//        return createVideo(prediction, voiceFile);
        return new File(script);
    }

    private String buildScript(PredictionData prediction) {

        return """
                AI predicts %s versus %s.
                
                Home win probability %s percent.
                
                Draw probability %s percent.
                
                Away win probability %s percent.
                
                Confidence score %s percent.
                
                Join our Telegram channel for more predictions.
                """
                .formatted(
                        prediction.getHomeTeam(),
                        prediction.getAwayTeam(),
                        prediction.getHomeProb(),
                        prediction.getDrawProb(),
                        prediction.getAwayProb(),
                        prediction.getEdgePercentage()
                );
    }
}
