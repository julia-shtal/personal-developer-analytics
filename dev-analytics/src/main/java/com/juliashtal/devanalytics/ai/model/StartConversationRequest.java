package com.juliashtal.devanalytics.ai.model;

import lombok.Data;

/**
 * Request body for starting an AI conversation seeded with a metrics summary.
 */
@Data
public class StartConversationRequest {
    private String summaryScope;
    private String summaryJson;
}
