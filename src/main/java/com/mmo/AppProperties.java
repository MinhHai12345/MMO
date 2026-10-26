package com.mmo;

import com.mmo.module.fb.ingest.enums.Provider;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Strongly typed access to application-specific configuration under the {@code app} prefix.
 *
 * <p>Framework configuration (Spring, datasource, Hibernate, Quartz and server) stays in
 * {@code application.yml}; this class contains only application-owned settings.</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Content content = new Content();
    private InitialData initialData = new InitialData();
    private Telegram telegram = new Telegram();
    private Twitter twitter = new Twitter();
    private Facebook facebook = new Facebook();
    private Stripe stripe = new Stripe();
    private SofaScore sofaScore = new SofaScore();
    private Sync sync = new Sync();
    private Ingestion ingestion = new Ingestion();
    private ApiFootball apiFootball = new ApiFootball();
    private FootballData footballData = new FootballData();
    private Understats understats = new Understats();

    @Getter
    @Setter
    public static class Content {
        private Publishing publishing = new Publishing();

        @Getter
        @Setter
        public static class Publishing {
            /** Shared API key for the content-publishing endpoint; never use it as a social provider token. */
            private String apiKey = "haivo";
        }
    }

    @Getter
    @Setter
    public static class InitialData {
        private boolean autoImport = true;
    }

    @Getter
    @Setter
    public static class Telegram {
        private Bot bot = new Bot();
        private Channel channel = new Channel();

        @Getter
        @Setter
        public static class Bot {
            private String token = "mock";
            private String username = "H2_FB_Bot";
        }

        @Getter
        @Setter
        public static class Channel {
            private String id = "mock";
            private String free = "mock";
            private String premium = "mock";
        }
    }

    @Getter
    @Setter
    public static class Twitter {
        private Consumer consumer = new Consumer();
        private Access access = new Access();

        @Getter
        @Setter
        public static class Consumer {
            private String key = "mock";
            private String secret = "mock";
        }

        @Getter
        @Setter
        public static class Access {
            private String token = "mock";
            private String secret = "mock";
        }
    }

    @Getter
    @Setter
    public static class Facebook {
        private Page page = new Page();
        private Access access = new Access();

        @Getter
        @Setter
        public static class Page {
            private String id = "mock";
        }

        @Getter
        @Setter
        public static class Access {
            private String token = "mock";
        }
    }

    @Getter
    @Setter
    public static class Stripe {
        private Api api = new Api();

        @Getter
        @Setter
        public static class Api {
            private String key = "mock";
            private Webhook webhook = new Webhook();
        }

        @Getter
        @Setter
        public static class Webhook {
            private String secret = "mock";
        }
    }

    @Getter
    @Setter
    public static class SofaScore {
        @NotBlank
        private String api = "https://api.sofascore.com/api/v1/";
        @NotBlank
        private String url = "https://www.sofascore.com";
        /** Default tournament catalogue region/language, for example EN or VN. */
        private String tournamentRegion = "EN";
    }

    @Getter
    @Setter
    public static class Sync {
        private boolean bootstrapOnStart = true;
        /** Optional allow-list of SofaScore unique tournament IDs for bounded catalogue sync. */
        private Set<Long> tournamentIds = new HashSet<>();
        private int seasonsPerLeague = 3;
        private int maxRounds = 50;
        /** Maximum SofaScore event pages to read per direction (finished and upcoming). */
        private int maxEventPages = 20;
    }

    /** Select the data source independently for each ingestion resource. */
    @Getter
    @Setter
    public static class Ingestion {
        private Providers providers = new Providers();
    }

    @Getter
    @Setter
    public static class Providers {
        private Provider league = Provider.FOOTBALL_DATA;
        private Provider team = Provider.FOOTBALL_DATA;
        private Provider fixture = Provider.FOOTBALL_DATA;
        private Provider standings = Provider.FOOTBALL_DATA;
        private Provider odds = Provider.API_FOOTBALL;
    }

    @Getter
    @Setter
    public static class ApiFootball {
        private boolean enabled = true;
        private String baseUrl = "https://v3.football.api-sports.io";
        private String apiKey = "mock";
        /** API-Football league IDs to sync; an empty list intentionally prevents league ingestion. */
        private List<Long> leagueIds = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class FootballData {
        private boolean enabled = true;
        private String baseUrl = "https://api.football-data.org/v4";
        private String apiKey = "mock";
        /** Numeric football-data.org competition IDs, e.g. 2021 (Premier League). */
        private List<Long> competitionIds = new ArrayList<>();
        /** Season start years to backfill; an empty list means current seasons only. */
        private List<String> seasons = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Understats {
        private String baseUrl = "https://understat.com";
    }
}
