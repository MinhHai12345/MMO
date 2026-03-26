package com.mmo.module.fb.channel.service;

import com.mmo.module.fb.model.MatchPrediction;

public interface TelegramService {
    void publish(MatchPrediction prediction);

    void publish();
}
