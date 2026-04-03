package com.cryptopay.malldemo.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HmacUtilTest {
    @Test
    void shouldGenerateExpectedHmacSha256Hex() {
        String actual = HmacUtil.hmacSha256Hex("secret", "hello");
        assertEquals("88aab3ede8d3adf94d26ab90d3bafd4a2083070c3bcce9c014ee04a443847c0b", actual);
    }

    @Test
    void shouldGenerateCanonicalWebhookSignature() {
        String actual = HmacUtil.hmacSha256Hex("secret", "1700000000", "wh_1", "{\"id\":\"evt_1\"}");
        assertTrue(actual != null && actual.length() == 64);
    }
}
