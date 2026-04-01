package com.personalspace.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TodoResponse(
    UUID id,
    String title,
    boolean completed,
    boolean archived,
    Instant createdAt,
    Instant updatedAt
) {}