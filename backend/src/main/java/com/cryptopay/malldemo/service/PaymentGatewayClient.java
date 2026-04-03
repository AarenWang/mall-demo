package com.cryptopay.malldemo.service;

import com.cryptopay.malldemo.config.MallDemoProperties;
import com.cryptopay.malldemo.config.MallDemoProperties.CryptoOption;
import com.cryptopay.malldemo.config.MallDemoProperties.MerchantCredentials;
import com.cryptopay.malldemo.util.BizException;
import com.cryptopay.malldemo.util.HmacUtil;
import com.cryptopay.malldemo.util.MallMerchantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PaymentGatewayClient {
    private final MallDemoProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PaymentGatewayClient(MallDemoProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(properties.getPayment().getConnectTimeoutMs()))
            .build();
    }

    public PaymentCreateResult createPayment(String orderId,
                                             String fiatCurrency,
                                             BigDecimal fiatAmount,
                                             String title,
                                             String description,
                                             String returnUrl,
                                             String cancelUrl,
                                             String idempotencyKey) {
        String path = "/api/v1/payments";
        ObjectNode body = objectMapper.createObjectNode();
        body.put("orderId", orderId);
        body.put("amount", fiatAmount);
        body.put("fiatCurrency", fiatCurrency);
        if (title != null && !title.isBlank()) {
            body.put("title", title);
        }
        if (description != null && !description.isBlank()) {
            body.put("description", description);
        }
        body.put("expiresIn", 900);
        if (returnUrl != null && !returnUrl.isBlank()) {
            body.put("returnUrl", returnUrl);
        }
        if (cancelUrl != null && !cancelUrl.isBlank()) {
            body.put("cancelUrl", cancelUrl);
        }

        ArrayNode optionsNode = body.putArray("cryptoOptions");
        List<CryptoOption> cryptoOptions = properties.getCryptoOptions();
        if (cryptoOptions != null) {
            for (CryptoOption option : cryptoOptions) {
                if (option.getChain() == null || option.getAsset() == null) {
                    continue;
                }
                ObjectNode item = optionsNode.addObject();
                item.put("chain", option.getChain());
                item.put("asset", option.getAsset());
            }
        }
        if (optionsNode.isEmpty()) {
            optionsNode.addObject().put("chain", "ANVIL").put("asset", "USDT");
        }

        JsonNode data = signedPost(path, body, idempotencyKey);
        String paymentId = firstNonBlank(
            text(data, "paymentId"),
            text(data, "paymentNo"),
            text(data, "payment_id"),
            text(data, "payment_no")
        );
        if (paymentId == null || paymentId.isBlank()) {
            throw new BizException(50000, "Payment gateway missing paymentId");
        }

        String status = text(data, "status");
        String checkoutUrl = buildCheckoutUrl(paymentId);

        PaymentCreateResult result = new PaymentCreateResult();
        result.setPaymentId(paymentId);
        result.setStatus(status == null ? "CREATED" : status);
        result.setCheckoutUrl(checkoutUrl);
        result.setRawData(data.toString());
        return result;
    }

    public String getPaymentStatus(String paymentId) {
        String path = "/api/v1/payments/" + paymentId;
        JsonNode data = signedGet(path);
        String status = text(data, "status");
        if (status == null || status.isBlank()) {
            throw new BizException(50000, "Payment gateway missing status");
        }
        return status;
    }

    /**
     * Get the current merchant's API credentials from context.
     * Falls back to legacy properties if merchants map is not configured.
     */
    private MerchantCredentials getCurrentMerchantCredentials() {
        String merchantId = MallMerchantContext.getMerchantId();
        if (merchantId == null) {
            merchantId = "merchant_a";
        }

        // Try to get from multi-merchant config
        if (properties.getMerchants() != null && properties.getMerchants().containsKey(merchantId)) {
            MerchantCredentials creds = properties.getMerchants().get(merchantId);
            if (creds.getApiKey() != null && creds.getApiSecret() != null) {
                return creds;
            }
        }

        // Fallback to legacy single-merchant config
        MerchantCredentials creds = new MerchantCredentials();
        creds.setApiKey(properties.getPayment().getApiKey());
        creds.setApiSecret(properties.getPayment().getApiSecret());
        creds.setName(merchantId);
        return creds;
    }

    private JsonNode signedGet(String path) {
        MerchantCredentials creds = getCurrentMerchantCredentials();
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String canonical = "GET\n" + path + "\n\n\n" + timestamp + "\n" + nonce;
        String signature = HmacUtil.hmacSha256Hex(creds.getApiSecret(), canonical);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(properties.getPayment().getBaseUrl() + path))
            .timeout(Duration.ofMillis(properties.getPayment().getReadTimeoutMs()))
            .header("X-Api-Key", creds.getApiKey())
            .header("X-Signature", signature)
            .header("X-Timestamp", String.valueOf(timestamp))
            .header("X-Nonce", nonce)
            .GET()
            .build();

        return sendAndParse(request);
    }

    private JsonNode signedPost(String path, ObjectNode body, String idempotencyKey) {
        try {
            MerchantCredentials creds = getCurrentMerchantCredentials();
            String rawBody = objectMapper.writeValueAsString(body);
            long timestamp = System.currentTimeMillis();
            String nonce = UUID.randomUUID().toString().replace("-", "");
            String canonical = "POST\n" + path + "\n\n" + rawBody + "\n" + timestamp + "\n" + nonce;
            String signature = HmacUtil.hmacSha256Hex(creds.getApiSecret(), canonical);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(properties.getPayment().getBaseUrl() + path))
                .timeout(Duration.ofMillis(properties.getPayment().getReadTimeoutMs()))
                .header("Content-Type", "application/json")
                .header("X-Api-Key", creds.getApiKey())
                .header("X-Signature", signature)
                .header("X-Timestamp", String.valueOf(timestamp))
                .header("X-Nonce", nonce)
                .POST(HttpRequest.BodyPublishers.ofString(rawBody));

            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                builder.header("X-Idempotency-Key", idempotencyKey);
            }

            return sendAndParse(builder.build());
        } catch (IOException ex) {
            throw new BizException(50000, "Serialize payment request failed");
        }
    }

    private JsonNode sendAndParse(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String message = root.path("message").asText("Gateway business error");
                throw new BizException(code, message);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(50000, "Gateway HTTP status: " + response.statusCode());
            }
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                throw new BizException(50000, "Gateway response missing data");
            }
            return data;
        } catch (IOException ex) {
            throw new BizException(50000, "Parse gateway response failed");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(50000, "Gateway call interrupted");
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
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

    private String buildCheckoutUrl(String paymentId) {
        String base = properties.getPayment().getCheckoutBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return new StringBuilder(base).append("/pay/").append(paymentId).toString();
    }

    public static class PaymentCreateResult {
        private String paymentId;
        private String status;
        private String checkoutUrl;
        private String rawData;

        public String getPaymentId() {
            return paymentId;
        }

        public void setPaymentId(String paymentId) {
            this.paymentId = paymentId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getCheckoutUrl() {
            return checkoutUrl;
        }

        public void setCheckoutUrl(String checkoutUrl) {
            this.checkoutUrl = checkoutUrl;
        }

        public String getRawData() {
            return rawData;
        }

        public void setRawData(String rawData) {
            this.rawData = rawData;
        }
    }
}
