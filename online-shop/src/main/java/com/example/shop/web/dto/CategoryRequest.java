package com.example.shop.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "name is required")
        @Size(min = 2, max = 100, message = "name must be 2-100 characters")
        String name,

        @Size(max = 500, message = "description must be at most 500 characters")
        String description
) {
}
