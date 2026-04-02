package com.mmo.module.fb.channel.strategy;

import com.mmo.module.fb.channel.model.Platform;
import com.mmo.module.fb.model.MatchPrediction;

public interface ContentStrategy {

    String content(MatchPrediction prediction);

    void dailyMatchesInformation();

    Platform getPlatform();

}
