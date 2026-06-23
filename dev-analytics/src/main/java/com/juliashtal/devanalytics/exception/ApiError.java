package com.juliashtal.devanalytics.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiError", description = "Error response containing status, message, and timestamp")
public class ApiError {

    @Schema(description = "Date and time when the error occurred", example = "25-05-2025 14:12:34")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy HH:mm:ss", timezone = "UTC")
    private Instant timestamp;

    @Schema(description = "HTTP status code", example = "500")
    private int status;

    @Schema(description = "HTTP status reason", example = "Internal Server Error")
    private String error;

    @Schema(description = "User-facing error message", example = "Resource not found")
    private String message;

    @Schema(description = "Request path where the error occurred", example = "/api/datasources")
    private String path;

    @Schema(description = "Correlation ID for tracing the request", example = "550e8400-e29b-41d4-a716-446655440000")
    private String correlationId;
}
