package com.mmo.module.fb.video.service;

import com.mmo.module.fb.channel.model.PredictionData;
import com.mmo.module.fb.video.model.VideoContent;

import java.io.File;

public interface VideoGenerator {
    File generateVideo(PredictionData prediction);

    String generateScript(VideoContent content);
}
