package com.mmo.module.fb.ingest.application.service.masterdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmo.module.fb.ingest.application.model.masterdata.MasterDataFile;
import com.mmo.module.fb.ingest.application.model.masterdata.MasterDataReconciliationReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasterDataReconciliationServiceTest {

    @Mock private ObjectMapper objectMapper;
    @Mock private MasterDataExportService exportService;

    @InjectMocks
    private MasterDataReconciliationService service;

    @Test
    void shouldReportMissingMappingsAndDateDifferencesWithoutMutatingData() throws Exception {
        MasterDataFile canonical = canonical();
        MasterDataFile persisted = new MasterDataFile(
                List.of(new MasterDataFile.LeagueEntry(
                        "premier-league", "Premier League", "England", null, true, null,
                        List.of(new MasterDataFile.SeasonEntry(
                                "2025", false, true,
                                LocalDate.of(2025, 8, 16), LocalDate.of(2026, 5, 24), Map.of())),
                        Map.of(), Map.of())),
                List.of());
        when(objectMapper.readValue(any(InputStream.class), eq(MasterDataFile.class))).thenReturn(canonical);
        when(exportService.export()).thenReturn(persisted);

        MasterDataReconciliationReport report = service.reconcile();

        assertEquals("INCONSISTENT", report.status());
        assertFalse(report.consistent());
        assertEquals(1, report.missingMappings());
        assertEquals(2, report.dateDiscrepancies());
        assertTrue(report.discrepancies().stream()
                .anyMatch(item -> "externalMapping".equals(item.field())));
    }

    @Test
    void shouldReturnConsistentWhenCanonicalRecordsAndMappingsMatch() throws Exception {
        MasterDataFile canonical = canonical();
        when(objectMapper.readValue(any(InputStream.class), eq(MasterDataFile.class))).thenReturn(canonical);
        when(exportService.export()).thenReturn(canonical);

        MasterDataReconciliationReport report = service.reconcile();

        assertEquals("CONSISTENT", report.status());
        assertTrue(report.consistent());
        assertTrue(report.discrepancies().isEmpty());
    }

    private MasterDataFile canonical() {
        return new MasterDataFile(
                List.of(new MasterDataFile.LeagueEntry(
                        "premier-league", "Premier League", "England", null, true, null,
                        List.of(new MasterDataFile.SeasonEntry(
                                "2025", false, true,
                                LocalDate.of(2025, 8, 15), LocalDate.of(2026, 5, 24),
                                Map.of("SOFA_SCORE", "76986"))),
                        Map.of("SOFA_SCORE", "17"), Map.of())),
                List.of());
    }
}
