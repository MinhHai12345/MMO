package com.mmo.module.fb.ingest.initializer;

import com.mmo.module.fb.ingest.service.impl.FootballDataUkSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Initializes static ingestion reference data after the Spring application starts.
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final FootballDataUkSyncService footballDataUkSyncService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting Football-Data.co.uk reference data initialization");
        footballDataUkSyncService.initialLeagueMappings();
        log.info("Football-Data.co.uk reference data initialization completed");
    }
}
