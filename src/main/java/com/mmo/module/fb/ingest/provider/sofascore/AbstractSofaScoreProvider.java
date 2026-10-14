package com.mmo.module.fb.ingest.provider.sofascore;

import com.microsoft.playwright.Page;
import com.mmo.module.fb.domain.enums.Provider;
import com.mmo.module.fb.ingest.provider.service.SofaCrawlerService;
import com.mmo.module.fb.ingest.provider.strategy.AbstractCrawler;

import java.util.function.Function;

abstract class AbstractSofaScoreProvider extends AbstractCrawler {
    protected final SofaCrawlerService sofaCrawlerService;

    protected AbstractSofaScoreProvider(SofaCrawlerService sofaCrawlerService) {
        this.sofaCrawlerService = sofaCrawlerService;
    }

    @Override
    public final Provider getProvider() {
        return Provider.SOFA_SCORE;
    }

    protected final <T> T withPage(Function<Page, T> action) {
        Page page = null;
        try {
            page = createPage();
            return action.apply(page);
        } finally {
            closePage(page);
        }
    }
}
