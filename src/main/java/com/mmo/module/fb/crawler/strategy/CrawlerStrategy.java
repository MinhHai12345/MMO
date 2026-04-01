package com.mmo.module.fb.crawler.strategy;

import com.microsoft.playwright.Page;
import com.mmo.module.fb.crawler.model.Provider;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.entity.MatchOdds;
import com.mmo.module.fb.entity.Season;
import com.mmo.module.fb.entity.Team;

import java.util.List;

public interface CrawlerStrategy {

    List<League> fetchLeague();

    List<Season> fetchSeasonByLeague(League league);

    List<Match> fetchMatchesByRound(Page page, League league, int round);

    MatchOdds fetchMatchOddByMatchId(Page page, Match match);

    List<Team> fetchTeamsByLeague(Page page, League league);

    Provider getProvider();

    Page createPage();
}
