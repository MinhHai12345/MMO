package com.mmo.module.fb.crawler.strategy;

import com.microsoft.playwright.Page;
import com.mmo.module.fb.crawler.model.Provider;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.entity.MatchOdds;
import com.mmo.module.fb.entity.Season;
import com.mmo.module.fb.entity.Team;

import java.util.List;
import java.util.Set;

public interface CrawlerStrategy {

    List<League> fetchLeague();

    List<Season> fetchSeasonByLeague(League league);

    List<Match> fetchMatchesByRound(Page page, League league, int round);

    MatchOdds fetchMatchOddsByMatch(Page page, Match match);

    Match fetchMatchXG(Page page, Match match);

    List<Team> fetchTeamsByLeague(Page page, League league);

    Set<Long> fetchDailyUpComingMatches(Page page);

    Provider getProvider();

    Page createPage();
}
