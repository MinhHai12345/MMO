package com.mmo.module.fb.ingest.provider.service.impl;

import com.microsoft.playwright.Page;
import com.mmo.AppProperties;
import com.mmo.module.fb.domain.entity.Team;
import com.mmo.module.fb.ingest.provider.model.DynamicFetchResult;
import com.mmo.module.fb.ingest.provider.service.impl.SofaCrawlerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SofaCrawlerServiceImplTest {

    private StubSofaCrawlerService service;
    private Page page;

    @BeforeEach
    void setUp() {
        service = new StubSofaCrawlerService(new AppProperties());
        page = mock(Page.class);
    }

    @Test
    void shouldReturnEmptyCollectionsWhenStaticFetchFails() {
        assertTrue(service.fetchLeagues(page).isEmpty());
        assertTrue(service.fetchSeasonByTournamentId(17L, page).isEmpty());
        assertTrue(service.fetchStandingTotal(17L, 2025L, page).isEmpty());
        assertTrue(service.fetchMatchesByRound(17L, 2025L, 3, page).isEmpty());
        assertTrue(service.fetchMatchStatistics(123L, page).isEmpty());
        assertNull(service.fetchMatchById(123L, page));
    }

    @Test
    void shouldReturnEmptyCollectionsWhenDynamicFetchHasNoData() {
        assertTrue(service.fetchDailyMatchOdds(page).isEmpty());
        assertTrue(service.fetchMatchesDailyByTournamentId(17L, page).isEmpty());

        Team team = mock(Team.class);
        when(team.getSlug()).thenReturn("test-team");
        when(team.getExternalId()).thenReturn("123");

        assertTrue(service.fetchHistoriesMatchesByTeamIdAndIndex(team, 5, page).isEmpty());
        assertNotNull(service.fetchMatchesDaily(17L, page));
    }

    @Test
    void shouldBuildExpectedStaticEndpointUrls() {
        service.fetchLeagues(page);
        assertEquals(
                "https://api.sofascore.com/api/v1/config/default-unique-tournaments/VN/football",
                service.lastStaticUrl
        );

        service.fetchSeasonByTournamentId(17L, page);
        assertEquals(
                "https://api.sofascore.com/api/v1/unique-tournament/17/seasons",
                service.lastStaticUrl
        );

        service.fetchMatchById(123L, page);
        assertEquals("https://api.sofascore.com/api/v1/event/123", service.lastStaticUrl);
    }

    private static final class StubSofaCrawlerService extends SofaCrawlerServiceImpl {
        private String lastStaticUrl;

        private StubSofaCrawlerService(AppProperties appProperties) {
            super(appProperties);
        }

        @Override
        protected <T> T safeFetch(String url, Page page, Class<T> clazz) {
            lastStaticUrl = url;
            return null;
        }

        @Override
        protected DynamicFetchResult safeFetch(
                String loadPageUrl,
                Map<String, Class<?>> endpointClassMap,
                Page page,
                Consumer<Page> triggerAction
        ) {
            return new DynamicFetchResult();
        }
    }
}
