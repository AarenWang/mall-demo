package com.cryptopay.malldemo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cryptopay.malldemo.config.MallDemoProperties;
import com.cryptopay.malldemo.model.WebhookLog;
import com.cryptopay.malldemo.repository.WebhookLogRepository;
import com.cryptopay.malldemo.util.BizException;
import com.cryptopay.malldemo.util.HmacUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

@ExtendWith(MockitoExtension.class)
class MallWebhookServiceTest {
    @Mock
    private MallOrderService mallOrderService;
    @Mock
    private WebhookLogRepository webhookLogRepository;

    private MallWebhookService mallWebhookService;

    @BeforeEach
    void setUp() {
        MallDemoProperties properties = new MallDemoProperties();
        properties.getWebhook().setSecret("test-secret");
        mallWebhookService = new MallWebhookService(
            properties,
            mallOrderService,
            webhookLogRepository,
            new ObjectMapper()
        );
    }

    @Test
    void shouldRejectWebhookWhenSignatureIsInvalid() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Signature", "bad-signature");

        BizException ex = assertThrows(BizException.class,
            () -> mallWebhookService.process("{\"event\":\"payment.paid\"}", headers));

        assertEquals(40003, ex.getCode());
        verify(mallOrderService, never()).handleWebhook(anyString(), any());
    }

    @Test
    void shouldHandleWebhookWhenSignatureIsValid() {
        String rawBody = "{\"id\":\"evt_1\",\"event\":\"payment.paid\",\"order_id\":\"M20260207100001\",\"payment_id\":\"P-1\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Webhook-Id", "wh_123");
        headers.set("X-Signature", HmacUtil.hmacSha256Hex("test-secret", rawBody));

        when(webhookLogRepository.insertIfAbsent(any(WebhookLog.class))).thenReturn(true);

        mallWebhookService.process(rawBody, headers);

        verify(mallOrderService).handleWebhook(anyString(), any());
        verify(webhookLogRepository).markProcessed(anyString(), any());
    }

    @Test
    void shouldBeIdempotentWhenWebhookAlreadyExists() {
        String rawBody = "{\"id\":\"evt_2\",\"event\":\"payment.expired\",\"order_id\":\"M20260207100002\",\"payment_id\":\"P-2\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Webhook-Id", "wh_duplicated");
        headers.set("X-Signature", HmacUtil.hmacSha256Hex("test-secret", rawBody));

        when(webhookLogRepository.insertIfAbsent(any(WebhookLog.class))).thenReturn(false);

        mallWebhookService.process(rawBody, headers);

        verify(mallOrderService, never()).handleWebhook(anyString(), any());
        verify(webhookLogRepository, never()).markProcessed(anyString(), any());
    }
}
