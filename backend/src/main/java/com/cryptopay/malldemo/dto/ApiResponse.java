package com.cryptopay.malldemo.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private long timestamp;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = 0;
        response.message = "OK";
        response.data = data;
        response.timestamp = Instant.now().toEpochMilli();
        return response;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = code;
        response.message = message;
        response.timestamp = Instant.now().toEpochMilli();
        return response;
    }
}
