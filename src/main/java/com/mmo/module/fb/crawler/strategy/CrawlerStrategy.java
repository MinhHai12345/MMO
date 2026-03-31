package com.mmo.module.fb.crawler.strategy;

import com.mmo.module.fb.crawler.model.Provider;
import com.mmo.module.fb.entity.League;
import com.mmo.module.fb.entity.Season;

import java.util.List;

public interface CrawlerStrategy {

    List<League> fetchLeague();

    List<Season> fetchSeasonByLeague(League league);

    Provider getProvider();
}
