package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractSofaScoreClientTest {

    @Test
    void capturesMultipleApiUrlsFromOnePageNavigation() {
        Page page = mock(Page.class);
        AbstractSofaScoreClient client = new TestSofaScoreClient();
        String pageUrl = "https://www.sofascore.com/football";

        when(page.url()).thenReturn(pageUrl);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<Response> listener = invocation.getArgument(0);
            listener.accept(response("https://www.sofascore.com/api/v1/config/default-unique-tournaments/EN/football",
                    "{\"uniqueTournaments\":[]}"));
            listener.accept(response("https://www.sofascore.com/api/v1/sport/football/odds/1/2026-11-12",
                    "{\"odds\":{}}"));
            return null;
        }).when(page).onResponse(any());

        DynamicFetchResult result = client.collect(pageUrl, List.of(
                SofaScoreEndpointSpec.required("config/default-unique-tournaments/EN/football", Map.class),
                SofaScoreEndpointSpec.required("/api/v1/sport/football/odds/1/2026-11-12", Map.class)), page);

        assertTrue(result.hasData("config/default-unique-tournaments/EN/football"));
        assertTrue(result.hasData("/api/v1/sport/football/odds/1/2026-11-12"));
        assertEquals(Map.of("uniqueTournaments", List.of()),
                result.get("config/default-unique-tournaments/EN/football", Map.class));
        assertEquals(Map.of("odds", Map.of()),
                result.get("/api/v1/sport/football/odds/1/2026-11-12", Map.class));
    }

    @Test
    void missingOptionalEndpointDoesNotFailPageCollection() {
        Page page = mock(Page.class);
        AbstractSofaScoreClient client = new TestSofaScoreClient();
        String pageUrl = "https://www.sofascore.com/football";

        when(page.url()).thenReturn(pageUrl);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<Response> listener = invocation.getArgument(0);
            listener.accept(response("https://www.sofascore.com/api/v1/events/123",
                    "{\"events\":[]}"));
            return null;
        }).when(page).onResponse(any());

        DynamicFetchResult result = client.collect(pageUrl, List.of(
                SofaScoreEndpointSpec.required("/api/v1/events/123", Map.class),
                SofaScoreEndpointSpec.optional("/api/v1/odds/123", Map.class)), page);

        assertTrue(result.hasData("/api/v1/events/123"));
        assertFalse(result.hasData("/api/v1/odds/123"));
    }

    private Response response(String url, String body) {
        Response response = mock(Response.class);
        when(response.url()).thenReturn(url);
        when(response.ok()).thenReturn(true);
        when(response.text()).thenReturn(body);
        return response;
    }

    private static final class TestSofaScoreClient extends AbstractSofaScoreClient {
        private DynamicFetchResult collect(String pageUrl, List<SofaScoreEndpointSpec> specs, Page page) {
            return safeFetch(pageUrl, specs, page, null);
        }

        @Override
        protected boolean waitForManualCaptchaResolution(Page page, String requestedUrl) {
            return false;
        }
    }
}
