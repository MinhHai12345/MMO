package com.mmo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.mmo.module.fb.domain.enums.Provider;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @NotNull
    private InitialData initialData;

    @NotNull
    private Telegram telegram;

    @NotNull
    private SofaScore sofaScore;

    private Sync sync = new Sync();

    private ApiFootball apiFootball = new ApiFootball();

    /** Select the data source independently for each ingestion resource. */
    private Ingestion ingestion = new Ingestion();

    @Getter
    @Setter
    public static class Ingestion {
        private Providers providers = new Providers();
    }

    @Getter
    @Setter
    public static class Providers {
        private Provider league = Provider.API_FOOTBALL;
        private Provider team = Provider.API_FOOTBALL;
        private Provider fixture = Provider.API_FOOTBALL;
        private Provider standings = Provider.API_FOOTBALL;
        private Provider odds = Provider.API_FOOTBALL;
    }

    @Getter
    @Setter
    public static class InitialData {
        private boolean autoImport;
    }

    @Getter
    @Setter
    public static class Telegram {
        @NotNull
        private Bot bot;
        @NotNull
        private Channel channel;

        @Getter
        @Setter
        public static class Bot {
            @NotBlank
            private String token;
            @NotBlank
            private String username;
        }

        @Getter
        @Setter
        public static class Channel {
            @NotBlank
            private String free;
            @NotBlank
            private String premium;
        }
    }

    @Getter
    @Setter
    public static class SofaScore {
        @NotBlank
        private String api;
        @NotBlank
        private String url;
    }

    @Getter
    @Setter
    public static class Sync {
        private boolean bootstrapOnStart = true;
        private Set<Long> tournamentIds = new HashSet<>();
        private int seasonsPerLeague = 3;
        private int maxRounds = 50;
    }

    @Getter
    @Setter
    public static class ApiFootball {
        private String baseUrl = "https://v3.football.api-sports.io";
        private String apiKey;
        /**
         * API-Football league IDs to sync. Keep this list explicit to control API quota.
         */
        private List<Long> leagueIds = new ArrayList<>();
        /**
         * Season start years supported by API-Football, e.g. 2024, 2025.
         * Empty means the current calendar year only.
         */
        private List<Integer> seasons = new ArrayList<>();
        private boolean enabled = true;
    }
}
