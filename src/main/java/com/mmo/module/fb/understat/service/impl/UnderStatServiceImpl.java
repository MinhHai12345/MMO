package com.mmo.module.fb.understat.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.module.fb.understat.model.LeagueData;
import com.mmo.module.fb.understat.service.UnderStatService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

@Service
@RequiredArgsConstructor
public class UnderStatServiceImpl implements UnderStatService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnderStatServiceImpl.class);
    private final ObjectMapper objectMapper;

    @Override
    public LeagueData getLeagueData(String leagueName, String season) {
        String url = String.format("https://understat.com/getLeagueData/%s/%s", leagueName, season);
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Requested-With", "XMLHttpRequest")
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body();
            String json;
            if (isGzipped(response)) {
                GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(body));
                json = new String(gis.readAllBytes(), StandardCharsets.UTF_8);
            } else {
                json = new String(body, StandardCharsets.UTF_8);
            }
            return objectMapper.readValue(json, LeagueData.class);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return null;
        }
    }

    private boolean isGzipped(HttpResponse<byte[]> response) {
        return response.headers().firstValue("Content-Encoding")
                .map(v -> v.equalsIgnoreCase("gzip"))
                .orElse(false);
    }
}
