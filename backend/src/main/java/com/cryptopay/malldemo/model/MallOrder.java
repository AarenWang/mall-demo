package com.cryptopay.malldemo.model;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;

@Data
public class MallOrder {
    private String orderId;
    private String merchantId;
    private String paymentId;
    private String checkoutUrl;
    private String productName;
    private String productImage;
    private Integer quantity;
    private String fiatCurrency;
    private BigDecimal fiatAmount;
    private MallOrderStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
