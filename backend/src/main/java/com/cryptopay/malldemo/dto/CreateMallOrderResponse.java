package com.cryptopay.malldemo.dto;

import lombok.Data;

@Data
public class CreateMallOrderResponse {
    private String orderId;
    private String paymentId;
    private String checkoutUrl;
}
