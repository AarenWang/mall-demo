package com.cryptopay.malldemo.controller;

import com.cryptopay.malldemo.dto.ApiResponse;
import com.cryptopay.malldemo.dto.CreateMallOrderRequest;
import com.cryptopay.malldemo.dto.CreateMallOrderResponse;
import com.cryptopay.malldemo.dto.MallOrderStatusResponse;
import com.cryptopay.malldemo.service.MallOrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mall/orders")
public class MallOrderController {
    private final MallOrderService mallOrderService;

    public MallOrderController(MallOrderService mallOrderService) {
        this.mallOrderService = mallOrderService;
    }

    @GetMapping
    public ApiResponse<List<MallOrderStatusResponse>> listOrders(@RequestParam(defaultValue = "20") Integer limit,
                                                                    @RequestParam(defaultValue = "0") Integer offset) {
        return ApiResponse.ok(mallOrderService.listOrders(limit == null ? 20 : limit, offset == null ? 0 : offset));
    }

    @PostMapping
    public ApiResponse<CreateMallOrderResponse> createOrder(@Valid @RequestBody CreateMallOrderRequest request) {
        return ApiResponse.ok(mallOrderService.createOrder(request));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<MallOrderStatusResponse> getOrder(@PathVariable String orderId) {
        return ApiResponse.ok(mallOrderService.getOrder(orderId));
    }

    @GetMapping("/{orderId}/payment-status")
    public ApiResponse<MallOrderStatusResponse> getPaymentStatus(@PathVariable String orderId) {
        return ApiResponse.ok(mallOrderService.getOrderPaymentStatus(orderId));
    }
}
