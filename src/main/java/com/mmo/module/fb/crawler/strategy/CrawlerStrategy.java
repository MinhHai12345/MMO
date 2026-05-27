package com.mmo.module.fb.crawler.strategy;

import com.microsoft.playwright.Page;
import com.mmo.module.fb.crawler.model.enums.Provider;
import com.mmo.module.fb.crawler.model.sofa.SofaOddsData;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.entity.MatchOdds;
import com.mmo.module.fb.entity.Season;
import com.mmo.module.fb.entity.Team;

import java.util.List;
import java.util.Set;

public interface CrawlerStrategy {

    /**
     * Store Leagues to system
     */
    void storeLeagues();

    /**
     * Store seasons to system
     */
    void storeSeasons();

    /**
     * Store teams to system
     */
    void storeTeams();

    /**
     * Store matches to system
     */
    void storeMatches();

    /**
     * Store match statistics to system
     */
    void storeMatchStatistics();

    Provider getProvider();

    Page createPage();
}
