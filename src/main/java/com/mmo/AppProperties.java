package com.mmo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
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

    /**
     * Cấu hình đồng bộ dữ liệu.
     */
    @Getter
    @Setter
    public static class Sync {
        /** Tự nạp dữ liệu nền (giải, mùa, đội, lịch) khi app khởi động. */
        private boolean bootstrapOnStart = true;
        /** Danh sách unique-tournament id của SofaScore được bật active. Đây là nguồn sự thật duy nhất. */
        private Set<Long> tournamentIds = new HashSet<>();
        /** Số mùa gần nhất lưu cho mỗi giải. */
        private int seasonsPerLeague = 3;
        /** Số vòng tối đa khi quét lịch cả mùa. */
        private int maxRounds = 50;
    }
}
