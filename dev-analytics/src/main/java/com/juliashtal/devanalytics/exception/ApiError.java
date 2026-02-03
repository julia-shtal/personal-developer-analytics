package com.juliashtal.devanalytics.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Schema(name = "ApiError", description = "Error response containing status, message, and timestamp")
public class ApiError {
    @Schema(description = "Date and time when the error occurred", example = "25-05-2025 14:12:34")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy' 'HH:mm:ss")
    private LocalDateTime timestamp;

    @Schema(description = "HTTP status code", example = "500")
    private int status;

    @Schema(description = "HTTP status reason", example = "Internal Server Error")
    private String error;

    @Schema(description = "Detailed error message", example = "Null pointer exception")
    private String message;

    @Schema(description = "Request path where the error occurred", example = "/api/diagnostic-sheets/123")
    private String path;
}

