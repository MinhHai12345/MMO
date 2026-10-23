package com.mmo.module.fb.content.publish;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PublicationStateMachineTest {

    @Test
    void requiresApprovalBeforeScheduling() {
        assertEquals(PublicationStateMachine.State.NEEDS_REVIEW,
                PublicationStateMachine.transition(PublicationStateMachine.State.DRAFT,
                        PublicationStateMachine.State.NEEDS_REVIEW));
        assertThrows(IllegalStateException.class, () ->
                PublicationStateMachine.transition(PublicationStateMachine.State.DRAFT,
                        PublicationStateMachine.State.PUBLISHED));
    }

    @Test
    void permitsRetryAfterPublishFailure() {
        assertEquals(PublicationStateMachine.State.PUBLISHING,
                PublicationStateMachine.transition(PublicationStateMachine.State.FAILED,
                        PublicationStateMachine.State.PUBLISHING));
    }

    @Test
    void publishedIsTerminal() {
        assertThrows(IllegalStateException.class, () ->
                PublicationStateMachine.transition(PublicationStateMachine.State.PUBLISHED,
                        PublicationStateMachine.State.FAILED));
    }
}
