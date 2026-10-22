package com.mmo.module.fb.content.video.service.impl;

import com.mmo.module.fb.content.video.model.VideoContent;
import com.mmo.module.fb.publish.model.PredictionData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FfmpegVideoGeneratorTest {

    private final FfmpegVideoGenerator generator = new FfmpegVideoGenerator();

    @Test
    void doesNotPretendVideoWasRenderedWhenPipelineIsNotConfigured() {
        assertThrows(UnsupportedOperationException.class,
                () -> generator.generateVideo(new PredictionData()));
    }

    @Test
    void generatesNeutralScriptWithoutBettingCopy() {
        VideoContent content = VideoContent.builder()
                .hook("A detail worth watching")
                .title("Home vs Away")
                .prediction("The home side has created more chances recently")
                .score("2-1")
                .build();

        String script = generator.generateScript(content);

        assertTrue(script.contains("Home vs Away"));
        assertTrue(script.contains("not guarantees"));
        assertFalse(script.toLowerCase().contains("bet"));
    }

    @Test
    void rejectsMissingContent() {
        assertThrows(NullPointerException.class, () -> generator.generateScript(null));
    }
}
