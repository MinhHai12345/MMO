package com.mmo.module.crawler.service.impl;

import com.mmo.module.crawler.service.CrawlerService;
import com.mmo.understat.model.LeagueData;
import com.mmo.understat.service.UnderStatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CrawlerServiceImpl implements CrawlerService {
    private final UnderStatService underStatService;

    @Override
    public void crawler() {
        LeagueData data = underStatService.getLeagueData("EPL", "2025");
        List<LeagueData.MatchDate> dates = data.getDates();
        Map<String, LeagueData.TeamData> teamMap = data.getTeams();
        List<LeagueData.PlayerData> players = data.getPlayers();
    }
}
