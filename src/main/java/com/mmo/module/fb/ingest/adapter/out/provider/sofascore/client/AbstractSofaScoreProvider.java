package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client;

import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.function.Function;

/**
 * Shared page lifecycle for SofaScore ingestion adapters.
 * The singleton session manager owns Playwright and serializes access to its synchronous API.
 */
@Slf4j
public abstract class AbstractSofaScoreProvider {
    @Autowired
    private SofaScoreBrowserSessionManager browserSessionManager;

    protected final <T> T withPage(Function<Page, T> action) {
        if (action == null) {
            throw new IllegalArgumentException("SofaScore page action is required");
        }

        Page page = browserSessionManager.openPage();
        try {
            return action.apply(page);
        } finally {
            browserSessionManager.closePage(page);
        }
    }
}
