package com.mmo.module.fb.channel.strategy;

import com.mmo.module.fb.channel.model.Platform;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Match;

import java.util.List;
import java.util.Map;

public interface ContentStrategy {

    String buildMatchesDailyContent(Map<League, List<Match>> matchesByLeague);

    String buildMatchInsightsContent(Map<League, List<Match>> matchesByLeague);

    Platform getPlatform();

}
