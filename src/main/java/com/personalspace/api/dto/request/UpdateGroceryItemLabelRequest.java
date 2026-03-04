package com.personalspace.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateGroceryItemLabelRequest(
        @NotBlank(message = "Label name is required")
        @Size(max = 50, message = "Label name must not exceed 50 characters")
        String name
) {}
