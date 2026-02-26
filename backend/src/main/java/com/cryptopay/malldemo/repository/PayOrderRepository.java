package com.cryptopay.malldemo.repository;

import com.cryptopay.malldemo.model.PayOrder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PayOrderRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<PayOrder> rowMapper = this::mapRow;

    public PayOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(PayOrder payOrder) {
        jdbcTemplate.update(
            """
                INSERT INTO pay_order(
                    order_id, merchant_id, payment_id, checkout_url, return_url, api_idempotency_key,
                    fiat_currency, fiat_amount, status, raw_create_resp, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            payOrder.getOrderId(),
            payOrder.getMerchantId(),
            payOrder.getPaymentId(),
            payOrder.getCheckoutUrl(),
            payOrder.getReturnUrl(),
            payOrder.getApiIdempotencyKey(),
            payOrder.getFiatCurrency(),
            payOrder.getFiatAmount(),
            payOrder.getStatus(),
            payOrder.getRawCreateResp(),
            Timestamp.from(payOrder.getCreatedAt()),
            Timestamp.from(payOrder.getUpdatedAt())
        );
    }

    public Optional<PayOrder> findByOrderId(String orderId) {
        List<PayOrder> rows = jdbcTemplate.query(
            "SELECT * FROM pay_order WHERE order_id = ? LIMIT 1",
            rowMapper,
            orderId
        );
        return rows.stream().findFirst();
    }

    public Optional<PayOrder> findByPaymentId(String paymentId) {
        List<PayOrder> rows = jdbcTemplate.query(
            "SELECT * FROM pay_order WHERE payment_id = ? LIMIT 1",
            rowMapper,
            paymentId
        );
        return rows.stream().findFirst();
    }

    public void updateStatusByOrderId(String orderId, String status, Instant updatedAt) {
        jdbcTemplate.update(
            "UPDATE pay_order SET status = ?, updated_at = ? WHERE order_id = ?",
            status, Timestamp.from(updatedAt), orderId
        );
    }

    private PayOrder mapRow(ResultSet rs, int rowNum) throws SQLException {
        PayOrder payOrder = new PayOrder();
        payOrder.setId(rs.getLong("id"));
        payOrder.setOrderId(rs.getString("order_id"));
        payOrder.setMerchantId(rs.getString("merchant_id"));
        payOrder.setPaymentId(rs.getString("payment_id"));
        payOrder.setCheckoutUrl(rs.getString("checkout_url"));
        payOrder.setReturnUrl(rs.getString("return_url"));
        payOrder.setApiIdempotencyKey(rs.getString("api_idempotency_key"));
        payOrder.setFiatCurrency(rs.getString("fiat_currency"));
        payOrder.setFiatAmount(rs.getBigDecimal("fiat_amount"));
        payOrder.setStatus(rs.getString("status"));
        payOrder.setRawCreateResp(rs.getString("raw_create_resp"));
        payOrder.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
        payOrder.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
        return payOrder;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
