package com.mmo.module.fb.ingest.provider.sofascore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.mmo.AppProperties;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.sofascore.client.AbstractSofaScoreProvider;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreLineupsResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SofaScoreLineupsClient extends AbstractSofaScoreProvider {
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public SofaScoreLineupsResponseDTO fetchLineups(Long externalMatchId) {
        if (externalMatchId == null || externalMatchId <= 0) {
            throw new IllegalArgumentException("A positive SofaScore match ID is required");
        }
        return withPage(page -> fetchLineups(externalMatchId, page));
    }

    private SofaScoreLineupsResponseDTO fetchLineups(Long externalMatchId, Page page) {
        try {
            AppProperties.ProviderConfig config = properties.getIngestion().getProviderConfig().get(Provider.SOFA_SCORE);
            if (config == null || !config.isEnabled()) throw new IllegalStateException("SofaScore provider is disabled or not configured");
            String base = config.getUri();
            String separator = base.endsWith("/") ? "" : "/";
            String url = base + separator + "event/" + externalMatchId + "/lineups";
            page.waitForTimeout(3000);
            Response response = page.navigate(url, new Page.NavigateOptions().setTimeout(30000));
            if (response == null) {
                throw new IllegalStateException("SofaScore returned no HTTP response for lineups");
            }
            int status = response.status();
            if (status == 404) return null;
            if (status == 403 || status == 429) {
                throw new IllegalStateException("SofaScore lineups request was rejected with HTTP " + status);
            }
            if (!response.ok()) {
                throw new IllegalStateException("SofaScore lineups request failed with HTTP " + status);
            }
            return objectMapper.readValue(response.text(), SofaScoreLineupsResponseDTO.class);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read SofaScore lineups for match " + externalMatchId,
                    exception);
        }
    }
}
