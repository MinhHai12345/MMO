package com.mmo.module.fb.video.service.impl;

import com.mmo.module.fb.channel.model.PredictionData;
import com.mmo.module.fb.video.model.VideoContent;
import com.mmo.module.fb.video.service.VideoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class FfmpegVideoGenerator implements VideoGenerator {
    private static final List<String> HOOKS = List.of(
            "AI found a strong value bet today.",
            "One match stands out on today's card.",
            "Here's today's AI football pick.",
            "Our model found an interesting opportunity."
    );

    @Override
    public File generateVideo(PredictionData prediction) {

//        String script = generateScript(prediction);

//        File voiceFile = voiceService.generateVoice(script);

//        return createVideo(prediction, voiceFile);
        return new File("script");
    }

    @Override
    public String generateScript(VideoContent content) {
        String hook = HOOKS.get(ThreadLocalRandom.current().nextInt(HOOKS.size()));
        return """
                %s
                
                %s.
                
                Our model predicts %s to win.
                
                Most likely scorelines are %s.
                
                Follow for more AI football predictions.
                """
                .formatted(hook,
                        content.getTitle(),
                        content.getPrediction(),
                        content.getScore()
                );
    }
}
