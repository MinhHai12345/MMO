package com.mmo.module.fb.channel.service;

import com.mmo.module.fb.channel.model.PredictionData;

import java.io.File;

public interface VideoGenerator {
    File generate(PredictionData prediction);
}
