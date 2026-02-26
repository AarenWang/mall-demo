package com.cryptopay.malldemo.controller;

import com.cryptopay.malldemo.dto.ApiResponse;
import com.cryptopay.malldemo.service.MallWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mall/webhooks")
public class MallWebhookController {
    private final MallWebhookService mallWebhookService;
    public MallWebhookController(MallWebhookService mallWebhookService) {
        this.mallWebhookService = mallWebhookService;
    }

    @PostMapping("/all")
    public ApiResponse<String> receive(@RequestHeader HttpHeaders headers, HttpServletRequest request) throws IOException {
        String rawBody = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        mallWebhookService.process(rawBody, headers);
        return ApiResponse.ok("OK");
    }
}
