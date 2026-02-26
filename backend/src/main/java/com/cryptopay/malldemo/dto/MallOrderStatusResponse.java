package com.cryptopay.malldemo.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class MallOrderStatusResponse {
    private String orderId;
    private String paymentId;
    private String checkoutUrl;
    private String status;
    private String productName;
    private String productImage;
    private Integer quantity;
    private String fiatCurrency;
    private BigDecimal fiatAmount;
    private long lastUpdatedAt;
}
