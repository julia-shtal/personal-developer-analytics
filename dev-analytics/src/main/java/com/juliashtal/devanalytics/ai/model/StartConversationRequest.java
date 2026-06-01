package com.juliashtal.devanalytics.ai.model;

import lombok.Data;

@Data
public class StartConversationRequest {
    private String summaryScope;
    private String summaryJson;
}
