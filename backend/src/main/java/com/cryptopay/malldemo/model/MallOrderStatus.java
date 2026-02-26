package com.cryptopay.malldemo.model;

public enum MallOrderStatus {
    PENDING,
    PROCESSING,
    PARTIALLY_PAID,
    PAID,
    EXPIRED,
    CANCELLED,
    REFUNDED,
    FAILED;

    public boolean isTerminal() {
        return this == PAID || this == EXPIRED || this == CANCELLED || this == REFUNDED || this == FAILED;
    }
}
