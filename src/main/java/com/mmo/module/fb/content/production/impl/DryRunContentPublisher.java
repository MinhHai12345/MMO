package com.mmo.module.fb.content.production.impl;

import com.mmo.module.fb.content.production.ContentPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Safe publication adapter used until an official platform integration is
 * configured. It never performs network I/O and never claims a post was sent.
 */
@Component
public class DryRunContentPublisher implements ContentPublisher {

    @Override
    public String platform() {
        return "DRY_RUN";
    }

    @Override
    public PublishResult publish(PublishCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Publish command is required");
        }
        return new PublishResult(
                platform(),
                null,
                "DRY_RUN",
                "Payload validated; no external platform API was called.",
                Instant.now()
        );
    }
}
