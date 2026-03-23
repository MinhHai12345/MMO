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

    @Getter
    @Setter
    public static class InitialData {

        @NotBlank
        private boolean autoImport;

    }

}
