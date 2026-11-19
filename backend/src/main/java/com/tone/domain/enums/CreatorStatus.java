package com.tone.domain.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum CreatorStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED;

    private static final Map<CreatorStatus, Set<CreatorStatus>> ALLOWED_TRANSITIONS = Map.of(
            PENDING, EnumSet.of(APPROVED, REJECTED),
            APPROVED, EnumSet.of(SUSPENDED),
            REJECTED, EnumSet.of(PENDING),
            SUSPENDED, EnumSet.of(APPROVED)
    );

    public boolean canTransitionTo(CreatorStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
