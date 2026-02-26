package com.cryptopay.malldemo.model;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

@Data
public class PayOrder {
    private Long id;
    private String orderId;
    private String merchantId;
    private String paymentId;
    private String checkoutUrl;
    private String returnUrl;
    private String apiIdempotencyKey;
    private String fiatCurrency;
    private BigDecimal fiatAmount;
    private String status;
    private String rawCreateResp;
    private Instant createdAt;
    private Instant updatedAt;
}
