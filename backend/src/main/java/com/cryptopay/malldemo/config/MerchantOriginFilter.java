package com.cryptopay.malldemo.config;

import com.cryptopay.malldemo.util.MallMerchantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter to extract merchant ID from X-Merchant-Id header and set it in MallMerchantContext.
 * This enables multi-tenant support where different frontend ports can identify themselves.
 */
@Component
@Order(1)
public class MerchantOriginFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MerchantOriginFilter.class);
    private static final String HEADER_MERCHANT_ID = "X-Merchant-Id";
    private static final String DEFAULT_MERCHANT_ID = "merchant_a";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String merchantId = request.getHeader(HEADER_MERCHANT_ID);
        if (merchantId == null || merchantId.isBlank()) {
            merchantId = DEFAULT_MERCHANT_ID;
        }
        MallMerchantContext.setMerchantId(merchantId);
        log.info("Set mall merchant context: {}", merchantId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MallMerchantContext.clear();
        }
    }
}
