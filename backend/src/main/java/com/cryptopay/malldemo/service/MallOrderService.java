package com.cryptopay.malldemo.service;

import com.cryptopay.malldemo.config.MallDemoProperties;
import com.cryptopay.malldemo.dto.CreateMallOrderRequest;
import com.cryptopay.malldemo.dto.CreateMallOrderResponse;
import com.cryptopay.malldemo.dto.MallOrderStatusResponse;
import com.cryptopay.malldemo.model.MallOrder;
import com.cryptopay.malldemo.model.MallOrderStatus;
import com.cryptopay.malldemo.model.PayOrder;
import com.cryptopay.malldemo.repository.PayOrderRepository;
import com.cryptopay.malldemo.repository.UserOrderRepository;
import com.cryptopay.malldemo.util.BizException;
import com.cryptopay.malldemo.util.MallMerchantContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class MallOrderService {
    private static final DateTimeFormatter ORDER_PREFIX_FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT).withZone(ZoneOffset.UTC);

    private final PaymentGatewayClient paymentGatewayClient;
    private final MallDemoProperties properties;
    private final UserOrderRepository userOrderRepository;
    private final PayOrderRepository payOrderRepository;

    public MallOrderService(PaymentGatewayClient paymentGatewayClient,
                            MallDemoProperties properties,
                            UserOrderRepository userOrderRepository,
                            PayOrderRepository payOrderRepository) {
        this.paymentGatewayClient = paymentGatewayClient;
        this.properties = properties;
        this.userOrderRepository = userOrderRepository;
        this.payOrderRepository = payOrderRepository;
    }

    @Transactional
    public CreateMallOrderResponse createOrder(CreateMallOrderRequest request) {
        String orderId = generateOrderId();
        String idempotencyKey = "mall_" + orderId;
        String returnUrl = buildReturnUrl(orderId);
        String normalizedCurrency = request.getFiatCurrency().toUpperCase(Locale.ROOT);
        String merchantId = MallMerchantContext.getMerchantId();

        PaymentGatewayClient.PaymentCreateResult payment =
            paymentGatewayClient.createPayment(orderId, normalizedCurrency, request.getFiatAmount(),
                returnUrl, idempotencyKey);

        Instant now = Instant.now();

        MallOrder userOrder = new MallOrder();
        userOrder.setOrderId(orderId);
        userOrder.setMerchantId(merchantId);
        userOrder.setProductName(request.getProductName());
        userOrder.setProductImage(request.getProductImage());
        userOrder.setQuantity(request.getQuantity() == null || request.getQuantity() <= 0 ? 1 : request.getQuantity());
        userOrder.setFiatCurrency(normalizedCurrency);
        userOrder.setFiatAmount(request.getFiatAmount());
        userOrder.setStatus(mapGatewayStatus(payment.getStatus()));
        userOrder.setCreatedAt(now);
        userOrder.setUpdatedAt(now);
        userOrderRepository.insert(userOrder);

        PayOrder payOrder = new PayOrder();
        payOrder.setOrderId(orderId);
        payOrder.setMerchantId(merchantId);
        payOrder.setPaymentId(payment.getPaymentId());
        payOrder.setCheckoutUrl(payment.getCheckoutUrl());
        payOrder.setReturnUrl(returnUrl);
        payOrder.setApiIdempotencyKey(idempotencyKey);
        payOrder.setFiatCurrency(normalizedCurrency);
        payOrder.setFiatAmount(request.getFiatAmount());
        payOrder.setStatus(payment.getStatus());
        payOrder.setRawCreateResp(payment.getRawData());
        payOrder.setCreatedAt(now);
        payOrder.setUpdatedAt(now);
        payOrderRepository.insert(payOrder);

        CreateMallOrderResponse response = new CreateMallOrderResponse();
        response.setOrderId(orderId);
        response.setPaymentId(payOrder.getPaymentId());
        response.setCheckoutUrl(payOrder.getCheckoutUrl());
        log.info("Created mall order orderId={}, paymentId={}", orderId, payOrder.getPaymentId());
        return response;
    }

    public MallOrderStatusResponse getOrder(String orderId) {
        MallOrder userOrder = requireUserOrder(orderId);
        PayOrder payOrder = requirePayOrder(orderId);
        return toStatusResponse(userOrder, payOrder);
    }

    public List<MallOrderStatusResponse> listOrders(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return userOrderRepository.listRecentWithPayment(safeLimit).stream()
            .map(this::toStatusResponse)
            .toList();
    }

    @Transactional
    public MallOrderStatusResponse getOrderPaymentStatus(String orderId) {
        MallOrder userOrder = requireUserOrder(orderId);
        PayOrder payOrder = requirePayOrder(orderId);

        if (!userOrder.getStatus().isTerminal()) {
            try {
                String paymentStatus = paymentGatewayClient.getPaymentStatus(payOrder.getPaymentId());
                MallOrderStatus mappedStatus = mapGatewayStatus(paymentStatus);
                Instant now = Instant.now();

                if (mappedStatus != userOrder.getStatus()) {
                    userOrderRepository.updateStatus(orderId, mappedStatus, now);
                    userOrder.setStatus(mappedStatus);
                    userOrder.setUpdatedAt(now);
                }
                if (!paymentStatus.equalsIgnoreCase(payOrder.getStatus())) {
                    payOrderRepository.updateStatusByOrderId(orderId, paymentStatus, now);
                    payOrder.setStatus(paymentStatus);
                    payOrder.setUpdatedAt(now);
                }
            } catch (Exception ex) {
                log.warn("Query payment status failed for orderId={}: {}", orderId, ex.getMessage());
            }
        }
        return toStatusResponse(userOrder, payOrder);
    }

    @Transactional
    public void handleWebhook(String webhookId, JsonNode payload) {
        String paymentId = firstNonBlank(
            text(payload, "payment_id"),
            text(payload, "paymentId"),
            text(payload, "payment_no"),
            text(payload, "paymentNo")
        );
        String orderId = firstNonBlank(text(payload, "order_id"), text(payload, "orderId"));

        PayOrder payOrder = null;
        if ((orderId == null || orderId.isBlank()) && paymentId != null && !paymentId.isBlank()) {
            payOrder = payOrderRepository.findByPaymentId(paymentId).orElse(null);
            if (payOrder != null) {
                orderId = payOrder.getOrderId();
            }
        }

        if (orderId == null || orderId.isBlank()) {
            log.warn("Skip webhook {}: missing orderId/paymentId mapping", webhookId);
            return;
        }

        MallOrder userOrder = userOrderRepository.findByOrderId(orderId).orElse(null);
        if (userOrder == null) {
            log.warn("Skip webhook {}: order not found for orderId={}", webhookId, orderId);
            return;
        }

        if (payOrder == null) {
            payOrder = payOrderRepository.findByOrderId(orderId).orElse(null);
        }

        String event = text(payload, "event");
        String status = text(payload, "status");
        MallOrderStatus mappedStatus = mapWebhookStatus(event, status);
        Instant now = Instant.now();

        userOrderRepository.updateStatus(orderId, mappedStatus, now);
        if (payOrder != null) {
            payOrderRepository.updateStatusByOrderId(orderId, mappedStatus.name(), now);
        }
    }

    private MallOrder requireUserOrder(String orderId) {
        return userOrderRepository.findByOrderId(orderId)
            .orElseThrow(() -> new BizException(40001, "Order not found: " + orderId));
    }

    private PayOrder requirePayOrder(String orderId) {
        return payOrderRepository.findByOrderId(orderId)
            .orElseThrow(() -> new BizException(40001, "Pay order not found: " + orderId));
    }

    private MallOrderStatusResponse toStatusResponse(MallOrder userOrder, PayOrder payOrder) {
        MallOrderStatusResponse response = new MallOrderStatusResponse();
        response.setOrderId(userOrder.getOrderId());
        response.setPaymentId(payOrder.getPaymentId());
        response.setCheckoutUrl(payOrder.getCheckoutUrl());
        response.setStatus(userOrder.getStatus().name());
        response.setProductName(userOrder.getProductName());
        response.setProductImage(userOrder.getProductImage());
        response.setQuantity(userOrder.getQuantity());
        response.setFiatCurrency(userOrder.getFiatCurrency());
        response.setFiatAmount(userOrder.getFiatAmount());
        response.setLastUpdatedAt(userOrder.getUpdatedAt().toEpochMilli());
        return response;
    }

    private MallOrderStatusResponse toStatusResponse(MallOrder order) {
        MallOrderStatusResponse response = new MallOrderStatusResponse();
        response.setOrderId(order.getOrderId());
        response.setPaymentId(order.getPaymentId());
        response.setCheckoutUrl(order.getCheckoutUrl());
        response.setStatus(order.getStatus().name());
        response.setProductName(order.getProductName());
        response.setProductImage(order.getProductImage());
        response.setQuantity(order.getQuantity());
        response.setFiatCurrency(order.getFiatCurrency());
        response.setFiatAmount(order.getFiatAmount());
        Instant updatedAt = order.getUpdatedAt() == null ? order.getCreatedAt() : order.getUpdatedAt();
        response.setLastUpdatedAt(updatedAt == null ? Instant.now().toEpochMilli() : updatedAt.toEpochMilli());
        return response;
    }

    private String generateOrderId() {
        String datePart = ORDER_PREFIX_FORMATTER.format(Instant.now());
        int random = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "M" + datePart + random;
    }

    private String buildReturnUrl(String orderId) {
        StringBuilder builder = new StringBuilder(properties.getFrontendResultBaseUrl());
        String delimiter = properties.getFrontendResultBaseUrl().contains("?") ? "&" : "?";
        builder.append(delimiter).append("orderId=").append(orderId);
        return builder.toString();
    }

    private MallOrderStatus mapWebhookStatus(String event, String status) {
        if (event != null && !event.isBlank()) {
            return switch (event) {
                case "payment.paid" -> MallOrderStatus.PAID;
                case "payment.partially_paid" -> MallOrderStatus.PARTIALLY_PAID;
                case "payment.expired" -> MallOrderStatus.EXPIRED;
                case "payment.cancelled" -> MallOrderStatus.CANCELLED;
                case "payment.refunded" -> MallOrderStatus.REFUNDED;
                default -> mapGatewayStatus(status);
            };
        }
        return mapGatewayStatus(status);
    }

    private MallOrderStatus mapGatewayStatus(String status) {
        if (status == null || status.isBlank()) {
            return MallOrderStatus.PROCESSING;
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "CREATED" -> MallOrderStatus.PROCESSING;
            case "PAID", "LATE_PAID" -> MallOrderStatus.PAID;
            case "PARTIALLY_PAID" -> MallOrderStatus.PARTIALLY_PAID;
            case "EXPIRED" -> MallOrderStatus.EXPIRED;
            case "CANCELLED" -> MallOrderStatus.CANCELLED;
            case "REFUNDED" -> MallOrderStatus.REFUNDED;
            default -> MallOrderStatus.FAILED;
        };
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
