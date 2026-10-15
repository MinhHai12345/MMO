package com.mmo.module.fb.ingest.service.impl;

import com.mmo.module.fb.domain.repository.BookmakerRepository;
import com.mmo.module.fb.domain.repository.MatchOddsRepository;
import com.mmo.module.fb.domain.repository.MatchRepository;
import com.mmo.module.fb.domain.repository.OddsHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OddsPersistenceServiceImplTest {
    @Mock MatchRepository matchRepository;
    @Mock BookmakerRepository bookmakerRepository;
    @Mock MatchOddsRepository matchOddsRepository;
    @Mock OddsHistoryRepository oddsHistoryRepository;

    @Test
    void shouldFailFastWhenMatchDoesNotExist() {
        when(matchRepository.findById(99L)).thenReturn(Optional.empty());
        OddsPersistenceServiceImpl service = new OddsPersistenceServiceImpl(matchRepository, bookmakerRepository,
                matchOddsRepository, oddsHistoryRepository);

        assertThrows(IllegalArgumentException.class, () -> service.persistOdds(99L, List.of()));

        verify(matchRepository).findById(99L);
        verifyNoInteractions(bookmakerRepository, matchOddsRepository, oddsHistoryRepository);
    }
}