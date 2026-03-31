package com.mmo.module.fb.crawler.strategy;

import com.mmo.module.fb.crawler.model.Provider;
import com.mmo.module.fb.entity.League;

import java.util.List;

public interface CrawlerStrategy {

    List<League> crawlLeague();

    Provider getProvider();
}
