package com.juliashtal.devanalytics.ai.model;

import lombok.Data;

/**
 * Request body for posting a message to an AI conversation.
 */
@Data
public class SendMessageRequest {
    private String content;
}
