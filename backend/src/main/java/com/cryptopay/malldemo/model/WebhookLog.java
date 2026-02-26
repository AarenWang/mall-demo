package com.cryptopay.malldemo.model;

import java.time.Instant;
import lombok.Data;

@Data
public class WebhookLog {
    private Long id;
    private String webhookId;
    private String eventType;
    private String paymentId;
    private String orderId;
    private String signature;
    private Integer retryCount;
    private String headersJson;
    private String rawBody;
    private boolean processed;
    private String processError;
    private Instant receivedAt;
    private Instant processedAt;
}
