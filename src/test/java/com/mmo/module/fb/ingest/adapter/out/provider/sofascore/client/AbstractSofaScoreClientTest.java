package com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.dto.SofaScoreResponseDTO.SeasonsResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractSofaScoreClientTest {

    private static final String PAGE_URL = "https://www.sofascore.com/football";
    private static final String ENDPOINT = "unique-tournament/17/seasons";

    @Test
    void pumpsPlaywrightEventsUntilRequiredApiResponseIsCaptured() {
        Page page = mock(Page.class);
        AtomicReference<Consumer<Response>> responseListener = new AtomicReference<>();
        when(page.url()).thenReturn(PAGE_URL);

        doAnswer(invocation -> {
            responseListener.set(invocation.getArgument(0));
            return null;
        }).when(page).onResponse(any());

        doAnswer(invocation -> {
            responseListener.get().accept(response(
                    "https://www.sofascore.com/api/v1/" + ENDPOINT,
                    200,
                    true,
                    "{\"seasons\":[{\"id\":123,\"name\":\"2026/2027\",\"year\":\"2026/2027\",\"isCurrent\":true}]}"));
            return null;
        }).when(page).waitForTimeout(anyDouble());

        DynamicFetchResult result = new TestClient().fetch(page);

        assertTrue(result.hasData(ENDPOINT));
        assertEquals(1, result.get(ENDPOINT, SeasonsResponseDTO.class).seasons().size());
    }

    @Test
    void preservesHttpFailureFromRequiredEndpoint() {
        Page page = mock(Page.class);
        AtomicReference<Consumer<Response>> responseListener = new AtomicReference<>();
        when(page.url()).thenReturn(PAGE_URL);

        doAnswer(invocation -> {
            responseListener.set(invocation.getArgument(0));
            return null;
        }).when(page).onResponse(any());

        doAnswer(invocation -> {
            responseListener.get().accept(response(
                    "https://www.sofascore.com/api/v1/" + ENDPOINT,
                    403,
                    false,
                    "{\"error\":{\"reason\":\"challenge\"}}"));
            return null;
        }).when(page).waitForTimeout(anyDouble());

        SofaScoreFetchException exception = assertThrows(
                SofaScoreFetchException.class, () -> new TestClient().fetch(page));

        assertTrue(exception.getMessage().contains("HTTP 403"));
    }

    private Response response(String url, int status, boolean ok, String body) {
        Response response = mock(Response.class);
        when(response.url()).thenReturn(url);
        when(response.status()).thenReturn(status);
        when(response.ok()).thenReturn(ok);
        when(response.text()).thenReturn(body);
        return response;
    }

    private static final class TestClient extends AbstractSofaScoreClient {
        private DynamicFetchResult fetch(Page page) {
            return safeFetch(
                    SofaScorePageSpec.of(PAGE_URL,
                            SofaScoreEndpointSpec.required(ENDPOINT, SeasonsResponseDTO.class)),
                    page,
                    null);
        }
    }
}
