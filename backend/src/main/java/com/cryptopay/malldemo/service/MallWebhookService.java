package com.cryptopay.malldemo.service;

import com.cryptopay.malldemo.config.MallDemoProperties;
import com.cryptopay.malldemo.model.WebhookLog;
import com.cryptopay.malldemo.repository.WebhookLogRepository;
import com.cryptopay.malldemo.util.BizException;
import com.cryptopay.malldemo.util.HmacUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class MallWebhookService {
    private static final Logger log = LoggerFactory.getLogger(MallWebhookService.class);
    private final MallDemoProperties properties;
    private final MallOrderService mallOrderService;
    private final WebhookLogRepository webhookLogRepository;
    private final ObjectMapper objectMapper;

    public MallWebhookService(MallDemoProperties properties,
                              MallOrderService mallOrderService,
                              WebhookLogRepository webhookLogRepository,
                              ObjectMapper objectMapper) {
        this.properties = properties;
        this.mallOrderService = mallOrderService;
        this.webhookLogRepository = webhookLogRepository;
        this.objectMapper = objectMapper;
    }

    public void process(String rawBody, HttpHeaders headers) {
        // 打印所有 HTTP Headers
        log.info("========== Webhook Received ==========");
        log.info("HTTP Headers:");
        headers.forEach((key, value) -> log.info("  {}: {}", key, value));

        // 打印 Body
        log.info("Raw Body: {}", rawBody);
        log.info("======================================");

        String signature = headers.getFirst("X-Signature");
        if (signature == null || signature.isBlank()) {
            throw new BizException(40003, "Missing webhook signature");
        }

        String expected = HmacUtil.hmacSha256Hex(properties.getWebhook().getSecret(), rawBody);
        if (!expected.equalsIgnoreCase(signature)) {
            throw new BizException(40003, "Webhook signature verification failed");
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            throw new BizException(40001, "Invalid webhook payload");
        }

        // Note: MerchantOriginFilter automatically extracts X-Merchant-Id from webhook headers
        // and sets MallMerchantContext. No manual resolution needed.

        String webhookId = resolveWebhookId(headers, payload, rawBody);
        WebhookLog webhookLog = buildWebhookLog(webhookId, signature, rawBody, headers, payload);
        boolean inserted = webhookLogRepository.insertIfAbsent(webhookLog);
        if (!inserted) {
            return;
        }

        try {
            mallOrderService.handleWebhook(webhookId, payload);
            webhookLogRepository.markProcessed(webhookId, Instant.now());
        } catch (Exception ex) {
            webhookLogRepository.markFailed(webhookId, ex.getMessage());
            throw ex;
        }
    }

    private WebhookLog buildWebhookLog(String webhookId,
                                       String signature,
                                       String rawBody,
                                       HttpHeaders headers,
                                       JsonNode payload) {
        WebhookLog webhookLog = new WebhookLog();
        webhookLog.setWebhookId(webhookId);
        webhookLog.setEventType(text(payload, "event"));
        webhookLog.setPaymentId(firstNonBlank(
            text(payload, "payment_id"),
            text(payload, "paymentId"),
            text(payload, "payment_no"),
            text(payload, "paymentNo")
        ));
        webhookLog.setOrderId(firstNonBlank(text(payload, "order_id"), text(payload, "orderId")));
        webhookLog.setSignature(signature);
        webhookLog.setRetryCount(parseInt(headers.getFirst("X-Retry-Count")));
        webhookLog.setHeadersJson(toHeaderJson(headers));
        webhookLog.setRawBody(rawBody);
        webhookLog.setProcessed(false);
        webhookLog.setReceivedAt(Instant.now());
        return webhookLog;
    }

    private String resolveWebhookId(HttpHeaders headers, JsonNode payload, String rawBody) {
        String headerWebhookId = headers.getFirst("X-Webhook-Id");
        if (headerWebhookId != null && !headerWebhookId.isBlank()) {
            return headerWebhookId;
        }
        String payloadEventId = text(payload, "id");
        if (payloadEventId != null && !payloadEventId.isBlank()) {
            return "payload-" + payloadEventId;
        }
        return "body-" + HmacUtil.hmacSha256Hex("mall-demo", rawBody).substring(0, 32);
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String toHeaderJson(HttpHeaders headers) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("X-Webhook-Id", headers.getFirst("X-Webhook-Id"));
        values.put("X-Timestamp", headers.getFirst("X-Timestamp"));
        values.put("X-Retry-Count", headers.getFirst("X-Retry-Count"));
        values.put("Content-Type", headers.getFirst("Content-Type"));
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String text(JsonNode payload, String key) {
        JsonNode value = payload.path(key);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
