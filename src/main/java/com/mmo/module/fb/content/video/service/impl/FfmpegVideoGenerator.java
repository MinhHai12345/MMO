package com.mmo.module.fb.content.video.service.impl;

import com.mmo.module.fb.content.video.model.VideoContent;
import com.mmo.module.fb.content.video.service.VideoGenerator;
import com.mmo.module.fb.publish.model.PredictionData;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Objects;

@Service
public class FfmpegVideoGenerator implements VideoGenerator {

    @Override
    public File generateVideo(PredictionData prediction) {
        Objects.requireNonNull(prediction, "Prediction data is required");
        throw new UnsupportedOperationException(
                "Video rendering is not configured. Configure the FFmpeg render pipeline before generating a video."
        );
    }

    @Override
    public String generateScript(VideoContent content) {
        Objects.requireNonNull(content, "Video content is required");

        String title = textOrFallback(content.getTitle(), "Football match analysis");
        String hook = textOrFallback(content.getHook(), "Here is one detail worth watching.");
        String analysis = textOrFallback(content.getPrediction(),
                "The available data does not support a stronger conclusion yet.");
        String scoreContext = content.getScore() == null || content.getScore().isBlank()
                ? ""
                : "\n\nScore context: " + content.getScore().strip() + ".";

        return """
                %s

                %s

                %s%s

                Model estimates are uncertain and are not guarantees. Verify the underlying data before publication.
                """.formatted(hook, title, analysis, scoreContext).strip();
    }

    private static String textOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
