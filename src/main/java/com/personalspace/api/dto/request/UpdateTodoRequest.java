package com.personalspace.api.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateTodoRequest(
    @Size(max = 255, message = "Title must not exceed 255 characters")
    String title,
    Boolean completed,
    Boolean archived
) {}
