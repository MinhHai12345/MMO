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
 * Strongly typed access to application-owned configuration under {@code app}.
 *
 * <p>Framework configuration such as datasource, Hibernate, Quartz and server remains in
 * {@code application.yml}. Provider configuration belongs to {@code app.ingestion}; publishing
 * channel credentials belong to {@code app.social}.</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Content content = new Content();
    private InitialData initialData = new InitialData();
    private Social social = new Social();
    private Stripe stripe = new Stripe();
    private Ingestion ingestion = new Ingestion();
    private Sync sync = new Sync();

    /**
     * Backward-compatible accessors keep existing integrations source-compatible while the
     * configuration tree is organized under {@code app.social} and {@code app.ingestion}.
     */
    public Social.Telegram getTelegram() {
        return social.getTelegram();
    }

    public void setTelegram(Social.Telegram telegram) {
        social.setTelegram(telegram);
    }

    public Social.Twitter getTwitter() {
        return social.getTwitter();
    }

    public void setTwitter(Social.Twitter twitter) {
        social.setTwitter(twitter);
    }

    public Social.Facebook getFacebook() {
        return social.getFacebook();
    }

    public void setFacebook(Social.Facebook facebook) {
        social.setFacebook(facebook);
    }

    public Ingestion.SofaScore getSofaScore() {
        return ingestion.getSofaScore();
    }

    public void setSofaScore(Ingestion.SofaScore sofaScore) {
        ingestion.setSofaScore(sofaScore);
    }

    public Ingestion.ApiFootball getApiFootball() {
        return ingestion.getApiFootball();
    }

    public void setApiFootball(Ingestion.ApiFootball apiFootball) {
        ingestion.setApiFootball(apiFootball);
    }

    public Ingestion.FootballData getFootballData() {
        return ingestion.getFootballData();
    }

    public void setFootballData(Ingestion.FootballData footballData) {
        ingestion.setFootballData(footballData);
    }

    public Ingestion.Understats getUnderstats() {
        return ingestion.getUnderstats();
    }

    public void setUnderstats(Ingestion.Understats understats) {
        ingestion.setUnderstats(understats);
    }

    @Getter
    @Setter
    public static class Content {
        private Publishing publishing = new Publishing();

        @Getter
        @Setter
        public static class Publishing {
            /** Shared API key for the content-publishing endpoint, not a social provider token. */
            private String apiKey = "haivo";
        }
    }

    @Getter
    @Setter
    public static class InitialData {
        private boolean autoImport = true;
    }

    /** Credentials and destination identifiers for outbound social publishing. */
    @Getter
    @Setter
    public static class Social {
        private Telegram telegram = new Telegram();
        private Twitter twitter = new Twitter();
        private Facebook facebook = new Facebook();

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

    /** Synchronization bounds for ingestion bootstrap and backfill jobs. */
    @Getter
    @Setter
    public static class Sync {
        private boolean bootstrapOnStart = true;
        private Set<Long> tournamentIds = new HashSet<>();
        private int seasonsPerLeague = 3;
        private int maxRounds = 50;
        private int maxEventPages = 20;
    }

    /** External data providers and resource-to-provider routing all live under {@code app.ingestion}. */
    @Getter
    @Setter
    public static class Ingestion {
        private Providers providers = new Providers();
        private SofaScore sofaScore = new SofaScore();
        private ApiFootball apiFootball = new ApiFootball();
        private FootballData footballData = new FootballData();
        private Understats understats = new Understats();
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
    public static class SofaScore {
        @NotBlank
        private String api = "https://api.sofascore.com/api/v1/";
        @NotBlank
        private String url = "https://www.sofascore.com";
        private String tournamentRegion = "EN";
    }

    @Getter
    @Setter
    public static class ApiFootball {
        private boolean enabled = true;
        private String baseUrl = "https://v3.football.api-sports.io";
        private String apiKey = "mock";
        private List<Long> leagueIds = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class FootballData {
        private boolean enabled = true;
        private String baseUrl = "https://api.football-data.org/v4";
        private String apiKey = "mock";
        private List<Long> competitionIds = new ArrayList<>();
        private List<String> seasons = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Understats {
        private String baseUrl = "https://understat.com";
    }
}
