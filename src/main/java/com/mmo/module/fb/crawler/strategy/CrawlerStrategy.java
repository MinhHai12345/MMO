package com.mmo.module.fb.crawler.strategy;

import com.microsoft.playwright.Page;
import com.mmo.module.fb.crawler.model.enums.Provider;
import com.mmo.module.fb.crawler.model.sofa.SofaMatchesData;

import java.util.List;

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

    /**
     * Prepare daily match upcoming to system
     */
    void prepareMatchUpcomingDaily();

    /**
     * Prepare match results to system
     */
    void prepareMatchResultDaily();

    /**
     * Fetch latest histories match by team id
     */
    List<SofaMatchesData.SofaEventDTO> getLatestHistoriesMatchesByTeamId(Long sofaTeamId);

    /**
     * Provider for crawl data
     */
    Provider getProvider();

    /**
     * Initial page context
     */
    Page createPage();
}
