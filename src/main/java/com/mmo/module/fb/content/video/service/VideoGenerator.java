package com.mmo.module.fb.content.video.service;

import com.mmo.module.fb.content.video.model.VideoContent;
import com.mmo.module.fb.publish.model.PredictionData;

import java.io.File;

public interface VideoGenerator {
    File generateVideo(PredictionData prediction);

    String generateScript(VideoContent content);
}
