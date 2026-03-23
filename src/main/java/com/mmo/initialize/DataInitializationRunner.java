package com.mmo.initialize;

import com.mmo.configuration.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataInitializationRunner {
    private final List<DataInitializer> initializers;
    private final AppProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void initializer() {
        if (properties.getInitialData().isAutoImport()) {
            initializers.forEach(DataInitializer::initialize);
        }
    }

}
