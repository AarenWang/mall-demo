package com.cryptopay.malldemo.repository;

import com.cryptopay.malldemo.model.WebhookLog;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WebhookLogRepository {
    private final JdbcTemplate jdbcTemplate;

    public WebhookLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean insertIfAbsent(WebhookLog webhookLog) {
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO webhook_log(
                        webhook_id, event_type, payment_id, order_id, signature, retry_count,
                        headers_json, raw_body, processed, process_error, received_at, processed_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                webhookLog.getWebhookId(),
                webhookLog.getEventType(),
                webhookLog.getPaymentId(),
                webhookLog.getOrderId(),
                webhookLog.getSignature(),
                webhookLog.getRetryCount(),
                webhookLog.getHeadersJson(),
                webhookLog.getRawBody(),
                webhookLog.isProcessed() ? 1 : 0,
                webhookLog.getProcessError(),
                Timestamp.from(webhookLog.getReceivedAt()),
                webhookLog.getProcessedAt() == null ? null : Timestamp.from(webhookLog.getProcessedAt())
            );
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    public void markProcessed(String webhookId, Instant processedAt) {
        jdbcTemplate.update(
            "UPDATE webhook_log SET processed = 1, processed_at = ?, process_error = NULL WHERE webhook_id = ?",
            Timestamp.from(processedAt), webhookId
        );
    }

    public void markFailed(String webhookId, String reason) {
        jdbcTemplate.update(
            "UPDATE webhook_log SET processed = 0, process_error = ? WHERE webhook_id = ?",
            reason, webhookId
        );
    }
}
