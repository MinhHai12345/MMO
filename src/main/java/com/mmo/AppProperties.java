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
    private Publication publication = new Publication();

    /**
     * Backward-compatible accessors keep existing integrations source-compatible while the
     * configuration tree is organized under {@code app.social} and {@code app.ingestion}.
     */
    public Telegram getTelegram() {
        return social.getTelegram();
    }

    public void setTelegram(Telegram telegram) {
        social.setTelegram(telegram);
    }

    public Twitter getTwitter() {
        return social.getTwitter();
    }

    public void setTwitter(Twitter twitter) {
        social.setTwitter(twitter);
    }

    public Facebook getFacebook() {
        return social.getFacebook();
    }

    public void setFacebook(Facebook facebook) {
        social.setFacebook(facebook);
    }

    public SofaScore getSofaScore() {
        return ingestion.getSofaScore();
    }

    public void setSofaScore(SofaScore sofaScore) {
        ingestion.setSofaScore(sofaScore);
    }

    public ApiFootball getApiFootball() {
        return ingestion.getApiFootball();
    }

    public void setApiFootball(ApiFootball apiFootball) {
        ingestion.setApiFootball(apiFootball);
    }

    public FootballData getFootballData() {
        return ingestion.getFootballData();
    }

    public void setFootballData(FootballData footballData) {
        ingestion.setFootballData(footballData);
    }

    public Understats getUnderstats() {
        return ingestion.getUnderstats();
    }

    public void setUnderstats(Understats understats) {
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

    /** Operational controls for the persisted content-publication queue. */
    @Getter
    @Setter
    public static class Publication {
        private Worker worker = new Worker();

        @Getter
        @Setter
        public static class Worker {
            private int batchSize = 10;
            private int maxAttempts = 5;
            private long retryDelayMillis = 300_000L;
        }
    }

    /** Credentials and destination identifiers for outbound social publishing. */
    @Getter
    @Setter
    public static class Social {
        private AppProperties.Telegram telegram = new AppProperties.Telegram();
        private AppProperties.Twitter twitter = new AppProperties.Twitter();
        private AppProperties.Facebook facebook = new AppProperties.Facebook();


    }

    @Getter
    @Setter
    public static class Stripe {
        private Api api = new Api();

        @Getter
        @Setter
        public static class Api {
            private String key;
            private Webhook webhook = new Webhook();
        }

        @Getter
        @Setter
        public static class Webhook {
            private String secret;
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
        private AppProperties.SofaScore sofaScore = new AppProperties.SofaScore();
        private AppProperties.ApiFootball apiFootball = new AppProperties.ApiFootball();
        private AppProperties.FootballData footballData = new AppProperties.FootballData();
        private AppProperties.Understats understats = new AppProperties.Understats();
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

    /** Legacy nested types remain source-compatible; settings are bound under app.ingestion. */
    @Getter
    @Setter
    public static class SofaScore {
        @NotBlank
        private String api;
        @NotBlank
        private String url;
        private String tournamentRegion;
    }

    @Getter
    @Setter
    public static class ApiFootball {
        private boolean enabled = true;
        private String baseUrl;
        private String apiKey;
        private List<Long> leagueIds = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class FootballData {
        private boolean enabled = true;
        private String baseUrl;
        private String apiKey;
        private List<Long> competitionIds = new ArrayList<>();
        private List<String> seasons = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Understats {
        private String baseUrl;
    }

    @Getter
    @Setter
    public static class Telegram {
        private Bot bot = new Bot();
        private Channel channel = new Channel();
        @Getter @Setter
        public static class Bot {
            private String token;
            private String username;
        }
        @Getter @Setter
        public static class Channel {
            private String id;
            private String free;
            private String premium;
        }
    }

    @Getter
    @Setter
    public static class Twitter {
        private Consumer consumer = new Consumer();
        private Access access = new Access();
        @Getter @Setter
        public static class Consumer {
            private String key;
            private String secret;
        }
        @Getter @Setter
        public static class Access {
            private String token;
            private String secret;
        }
    }

    @Getter
    @Setter
    public static class Facebook {
        private Page page = new Page();
        private Access access = new Access();
        @Getter @Setter
        public static class Page {
            private String id;
        }
        @Getter @Setter
        public static class Access {
            private String token;
        }
    }
}
