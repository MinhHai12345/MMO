package com.mmo.module.fb.content.publish;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Pure lifecycle rules for a future persisted publishing workflow.
 * A state transition alone never calls a social platform API.
 */
public final class PublicationStateMachine {
    private static final Map<State, Set<State>> ALLOWED = new EnumMap<>(State.class);

    static {
        ALLOWED.put(State.DRAFT, EnumSet.of(State.NEEDS_REVIEW, State.BLOCKED));
        ALLOWED.put(State.NEEDS_REVIEW, EnumSet.of(State.APPROVED, State.BLOCKED, State.DRAFT));
        ALLOWED.put(State.APPROVED, EnumSet.of(State.SCHEDULED, State.PUBLISHING, State.DRAFT));
        ALLOWED.put(State.SCHEDULED, EnumSet.of(State.PUBLISHING, State.CANCELLED));
        ALLOWED.put(State.PUBLISHING, EnumSet.of(State.PUBLISHED, State.FAILED));
        ALLOWED.put(State.FAILED, EnumSet.of(State.SCHEDULED, State.PUBLISHING, State.CANCELLED));
        ALLOWED.put(State.BLOCKED, EnumSet.of(State.DRAFT, State.NEEDS_REVIEW));
        ALLOWED.put(State.PUBLISHED, EnumSet.noneOf(State.class));
        ALLOWED.put(State.CANCELLED, EnumSet.noneOf(State.class));
    }

    private PublicationStateMachine() {}

    public static State transition(State current, State target) {
        if (current == null || target == null) {
            throw new IllegalArgumentException("current and target states are required");
        }
        if (!ALLOWED.get(current).contains(target)) {
            throw new IllegalStateException("Illegal publication transition: " + current + " -> " + target);
        }
        return target;
    }

    public enum State {
        DRAFT, NEEDS_REVIEW, APPROVED, SCHEDULED, PUBLISHING, PUBLISHED, FAILED, BLOCKED, CANCELLED
    }
}
