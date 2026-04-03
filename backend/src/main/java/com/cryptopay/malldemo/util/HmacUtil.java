package com.cryptopay.malldemo.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacUtil {
    private HmacUtil() {
    }

    public static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(key);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build HMAC", ex);
        }
    }

    public static String hmacSha256Hex(String secret, String timestamp, String deliveryId, String payload) {
        return hmacSha256Hex(secret, canonicalPayload(timestamp, deliveryId, payload));
    }

    public static String canonicalPayload(String timestamp, String deliveryId, String payload) {
        return String.valueOf(timestamp) + "." + String.valueOf(deliveryId) + "." + (payload == null ? "" : payload);
    }

    public static boolean secureEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
            expected.trim().toLowerCase().getBytes(StandardCharsets.UTF_8),
            actual.trim().toLowerCase().getBytes(StandardCharsets.UTF_8)
        );
    }
}
