package com.juliashtal.devanalytics.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void timestamp_acceptsInstant() {
        ApiError error = ApiError.builder()
                .timestamp(Instant.EPOCH)
                .status(400)
                .error("Bad Request")
                .message("test error")
                .build();

        assertThat(error.getTimestamp()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void timestamp_serialisesAsFormattedUtcString() throws Exception {
        // 2025-05-25T14:12:34Z in epoch seconds
        Instant ts = Instant.parse("2025-05-25T14:12:34Z");
        ApiError error = ApiError.builder()
                .timestamp(ts)
                .status(500)
                .error("Internal Server Error")
                .message("something broke")
                .build();

        String json = mapper.writeValueAsString(error);

        assertThat(json).contains("\"25-05-2025 14:12:34\"");
    }
}
