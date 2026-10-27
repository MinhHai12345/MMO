package com.mmo.module.fb.publish.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationStateMachineTest {

    @Test
    void ambiguousDeliveryCanOnlyBeReconciledExplicitly() {
        assertEquals(PublicationStateMachine.State.DELIVERY_UNKNOWN,
                PublicationStateMachine.transition(
                        PublicationStateMachine.State.PUBLISHING,
                        PublicationStateMachine.State.DELIVERY_UNKNOWN));
        assertEquals(PublicationStateMachine.State.PUBLISHED,
                PublicationStateMachine.transition(
                        PublicationStateMachine.State.DELIVERY_UNKNOWN,
                        PublicationStateMachine.State.PUBLISHED));
        assertEquals(PublicationStateMachine.State.FAILED,
                PublicationStateMachine.transition(
                        PublicationStateMachine.State.DELIVERY_UNKNOWN,
                        PublicationStateMachine.State.FAILED));
    }

    @Test
    void unknownDeliveryCannotBeSentAgainWithoutReconciliation() {
        assertThrows(IllegalStateException.class,
                () -> PublicationStateMachine.transition(
                        PublicationStateMachine.State.DELIVERY_UNKNOWN,
                        PublicationStateMachine.State.PUBLISHING));
    }

    @Test
    void publishedPublicationIsTerminal() {
        assertThrows(IllegalStateException.class,
                () -> PublicationStateMachine.transition(
                        PublicationStateMachine.State.PUBLISHED,
                        PublicationStateMachine.State.PUBLISHING));
    }
}
