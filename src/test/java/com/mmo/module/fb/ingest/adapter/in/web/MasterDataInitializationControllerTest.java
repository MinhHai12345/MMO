package com.mmo.module.fb.ingest.adapter.in.web;

import com.mmo.module.fb.ingest.application.model.masterdata.MasterDataInitializationReport;
import com.mmo.module.fb.ingest.application.service.masterdata.MasterDataInitializationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MasterDataInitializationControllerTest {

    private MasterDataInitializationService service;
    private MasterDataInitializationController controller;

    @BeforeEach
    void setUp() {
        service = mock(MasterDataInitializationService.class);
        controller = new MasterDataInitializationController(service);
        ReflectionTestUtils.setField(controller, "configuredApiKey", "test-admin-key");
    }

    @Test
    void shouldRejectRequestWithoutValidAdminKey() {
        ResponseEntity<?> response = controller.initialize("invalid", new MasterDataInitializationController.InitializationRequest("DRY_RUN", false));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    void shouldUseDryRunWhenModeIsOmittedAndNeverApplyChanges() {
        when(service.preview()).thenReturn(report("DRY_RUN"));

        ResponseEntity<?> response = controller.initialize("test-admin-key", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).preview();
        verify(service, never()).apply();
    }

    @Test
    void shouldRequireExplicitConfirmationBeforeApply() {
        ResponseEntity<?> response = controller.initialize("test-admin-key",
                new MasterDataInitializationController.InitializationRequest("APPLY", false));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(service, never()).apply();
    }

    @Test
    void shouldApplyOnlyAfterExplicitConfirmation() {
        when(service.apply()).thenReturn(report("APPLIED"));

        ResponseEntity<?> response = controller.initialize("test-admin-key",
                new MasterDataInitializationController.InitializationRequest("APPLY", true));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).apply();
        verify(service, never()).preview();
    }

    private MasterDataInitializationReport report(String mode) {
        return new MasterDataInitializationReport(mode, 0, 0, 0, 0, 0, 0, 0, List.of());
    }
}
