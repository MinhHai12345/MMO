package com.mmo.module.fb.analytics.service.impl;

import com.mmo.module.fb.domain.repository.MatchPredictionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WalkForwardBackTestServiceImplTest {

    @Mock
    private MatchPredictionRepository matchPredictionRepository;

    @InjectMocks
    private WalkForwardBackTestServiceImpl service;

    @Test
    void rejectsNullDateBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> service.run(null, Instant.parse("2026-10-21T00:00:00Z")));
        verifyNoInteractions(matchPredictionRepository);
    }

    @Test
    void rejectsReversedOrEmptyDateRange() {
        Instant start = Instant.parse("2026-10-21T00:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> service.run(start, start));
        assertThrows(IllegalArgumentException.class,
                () -> service.run(start.plusSeconds(60), start));
        verifyNoInteractions(matchPredictionRepository);
    }
}
