package com.cryptopay.malldemo.repository;

import com.cryptopay.malldemo.model.MallOrder;
import com.cryptopay.malldemo.model.MallOrderStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
public class UserOrderRepository {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<MallOrder> rowMapper = this::mapRow;

    public UserOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(MallOrder order) {
        jdbcTemplate.update(
            """
                INSERT INTO user_order(
                    order_id, merchant_id, product_name, product_image, quantity, fiat_currency, fiat_amount,
                    status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            order.getOrderId(),
            order.getMerchantId(),
            order.getProductName(),
            order.getProductImage(),
            order.getQuantity(),
            order.getFiatCurrency(),
            order.getFiatAmount(),
            order.getStatus().name(),
            Timestamp.from(order.getCreatedAt()),
            Timestamp.from(order.getUpdatedAt())
        );
    }

    public Optional<MallOrder> findByOrderId(String orderId) {
        List<MallOrder> rows = jdbcTemplate.query(
            "SELECT * FROM user_order WHERE order_id = ? LIMIT 1",
            rowMapper,
            orderId
        );
        return rows.stream().findFirst();
    }

    public void updateStatus(String orderId, MallOrderStatus status, Instant updatedAt) {
        jdbcTemplate.update(
            "UPDATE user_order SET status = ?, updated_at = ? WHERE order_id = ?",
            status.name(), Timestamp.from(updatedAt), orderId
        );
    }

    public List<MallOrder> listRecentWithPayment(String merchantId, int limit, int offset) {
        log.info("listRecentWithPayment: merchantId={}, limit={}, offset={}", merchantId, limit, offset);
        return jdbcTemplate.query(
            """
                SELECT
                    u.order_id,
                    u.merchant_id,
                    u.product_name,
                    u.product_image,
                    u.quantity,
                    u.fiat_currency,
                    u.fiat_amount,
                    u.status,
                    u.created_at,
                    u.updated_at,
                    p.payment_id,
                    p.checkout_url
                FROM user_order u
                LEFT JOIN pay_order p ON p.order_id = u.order_id
                WHERE u.merchant_id = ?
                ORDER BY u.created_at DESC
                LIMIT ? OFFSET ?
            """,
            (rs, rowNum) -> {
                MallOrder order = mapRow(rs, rowNum);
                order.setPaymentId(rs.getString("payment_id"));
                order.setCheckoutUrl(rs.getString("checkout_url"));
                return order;
            },
            merchantId,
            limit,
            offset
        );
    }

    private MallOrder mapRow(ResultSet rs, int rowNum) throws SQLException {
        MallOrder order = new MallOrder();
        order.setOrderId(rs.getString("order_id"));
        order.setMerchantId(rs.getString("merchant_id"));
        order.setProductName(rs.getString("product_name"));
        order.setProductImage(rs.getString("product_image"));
        order.setQuantity(rs.getInt("quantity"));
        order.setFiatCurrency(rs.getString("fiat_currency"));
        order.setFiatAmount(rs.getBigDecimal("fiat_amount"));
        order.setStatus(MallOrderStatus.valueOf(rs.getString("status")));
        order.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
        order.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
        return order;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
