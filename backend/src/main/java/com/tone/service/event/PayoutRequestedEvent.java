package com.tone.service.event;

import java.util.UUID;

public record PayoutRequestedEvent(UUID payoutId) {}
