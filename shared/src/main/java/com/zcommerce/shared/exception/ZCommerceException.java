package com.zcommerce.shared.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * Base exception class for all Z-Commerce application exceptions.
 * Provides structured error information with error codes and context.
 */
public abstract class ZCommerceException extends RuntimeException {
    private final String errorCode;
    private final Map<String, Object> context;

    protected ZCommerceException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.context = new HashMap<>();
    }

    protected ZCommerceException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = new HashMap<>();
    }

    protected ZCommerceException(String errorCode, String message, Map<String, Object> context) {
        super(message);
        this.errorCode = errorCode;
        this.context = new HashMap<>(context);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getContext() {
        return new HashMap<>(context);
    }

    public ZCommerceException addContext(String key, Object value) {
        this.context.put(key, value);
        return this;
    }
}