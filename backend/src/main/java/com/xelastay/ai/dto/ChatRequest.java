package com.xelastay.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "La pregunta es obligatoria")
        @Size(max = 1000, message = "La pregunta admite máximo 1000 caracteres") String question) {
}
