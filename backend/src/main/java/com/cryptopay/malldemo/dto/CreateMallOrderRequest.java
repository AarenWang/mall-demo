package com.cryptopay.malldemo.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class CreateMallOrderRequest {
    @NotBlank
    private String fiatCurrency;

    @NotNull
    @DecimalMin(value = "0.01")
    @DecimalMax(value = "100000.00")
    @Digits(integer = 6, fraction = 2)
    private BigDecimal fiatAmount;

    private String productName = "Mall Demo Product";
    private String productImage = "https://images.unsplash.com/photo-1523275335684-37898b6baf30";
    private Integer quantity = 1;
}
