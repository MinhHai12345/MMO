package com.mmo.module.fb.crawler.initialize;

import com.mmo.initialize.DataInitializer;
import com.mmo.module.fb.service.LeagueService;
import com.mmo.module.fb.service.MatchOddService;
import com.mmo.module.fb.service.MatchService;
import com.mmo.module.fb.service.SeasonService;
import com.mmo.module.fb.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

//@Component
@RequiredArgsConstructor
public class InitializeData implements DataInitializer {
    private final LeagueService leagueService;
    private final SeasonService seasonService;
    private final TeamService teamService;
    private final MatchService matchService;
    private final MatchOddService matchOddService;

    @Override
    public void initialize() {
        leagueService.storeAllLeagues();
        seasonService.storeAllSeasons();
        teamService.storeAllTeams();
        matchService.storeAllMatches();
        matchService.storeAllMatchXGs();
        matchOddService.storeAllMatchOdds();
    }
}
