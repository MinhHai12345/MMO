package com.mmo.module.fb.channel.service;

import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Match;

import java.util.List;
import java.util.Map;

public interface TelegramService {
    void notifyProcessMatches(Map<League, List<Match>> matchesByLeague);
}
