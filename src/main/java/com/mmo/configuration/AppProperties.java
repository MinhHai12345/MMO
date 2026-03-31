package com.mmo.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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

    @Getter
    @Setter
    public static class InitialData {

        @NotBlank
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
            private String id;
        }
    }

    @Getter
    @Setter
    public static class SofaScore {
        @NotBlank
        private String api;
    }
}
