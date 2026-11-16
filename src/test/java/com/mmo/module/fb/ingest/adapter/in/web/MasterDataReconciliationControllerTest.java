package com.mmo.module.fb.ingest.adapter.in.web;

import com.mmo.module.fb.ingest.application.model.masterdata.MasterDataReconciliationReport;
import com.mmo.module.fb.ingest.application.service.masterdata.MasterDataReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MasterDataReconciliationControllerTest {

    private MasterDataReconciliationService service;
    private MasterDataReconciliationController controller;

    @BeforeEach
    void setUp() {
        service = mock(MasterDataReconciliationService.class);
        controller = new MasterDataReconciliationController(service);
        ReflectionTestUtils.setField(controller, "configuredApiKey", "test-admin-key");
    }

    @Test
    void shouldRejectInvalidAdminKey() {
        ResponseEntity<?> response = controller.reconcile("invalid");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    void shouldReturnConflictWhenReconciliationFindsErrors() {
        when(service.reconcile()).thenReturn(new MasterDataReconciliationReport(
                "INCONSISTENT", 1, 0, 1, 0, 0, 0,
                1, 1, 0, 1, 0, 0, List.of()));

        ResponseEntity<?> response = controller.reconcile("test-admin-key");

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(service).reconcile();
    }

    @Test
    void shouldReturnOkWhenReconciliationIsConsistent() {
        when(service.reconcile()).thenReturn(new MasterDataReconciliationReport(
                "CONSISTENT", 1, 1, 1, 1, 0, 0,
                0, 0, 0, 0, 0, 0, List.of()));

        ResponseEntity<?> response = controller.reconcile("test-admin-key");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).reconcile();
    }
}
