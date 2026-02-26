package com.cryptopay.malldemo.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "mall.demo")
public class MallDemoProperties {
    private String frontendResultBaseUrl = "http://localhost:5174/result";
    private Payment payment = new Payment();
    private Webhook webhook = new Webhook();
    private List<CryptoOption> cryptoOptions = new ArrayList<>();
    private Map<String, MerchantCredentials> merchants = new HashMap<>();

    @Data
    public static class Payment {
        private String baseUrl = "http://localhost:8080";
        @Deprecated
        private String apiKey = "replace_me";
        @Deprecated
        private String apiSecret = "replace_me";
        private String checkoutBaseUrl = "http://localhost:5173";
        private long connectTimeoutMs = 3000;
        private long readTimeoutMs = 5000;
    }

    @Data
    public static class Webhook {
        private String secret = "replace_me";
    }

    @Data
    public static class CryptoOption {
        private String chain;
        private String asset;
    }

    @Data
    public static class MerchantCredentials {
        private String apiKey;
        private String apiSecret;
        private String name;
    }
}
