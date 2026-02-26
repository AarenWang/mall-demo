package com.cryptopay.malldemo.util;

/**
 * Mall demo multi-merchant context.
 * Holds the current merchant ID for the request scope.
 */
public final class MallMerchantContext {

    private static final ThreadLocal<String> MERCHANT_ID = new ThreadLocal<>();

    private MallMerchantContext() {
    }

    /**
     * Set the current merchant ID for this request.
     */
    public static void setMerchantId(String merchantId) {
        MERCHANT_ID.set(merchantId);
    }

    /**
     * Get the current merchant ID for this request.
     * @return merchant ID or null if not set
     */
    public static String getMerchantId() {
        return MERCHANT_ID.get();
    }

    /**
     * Clear the merchant ID for this request.
     * Should be called at the end of request processing.
     */
    public static void clear() {
        MERCHANT_ID.remove();
    }
}
