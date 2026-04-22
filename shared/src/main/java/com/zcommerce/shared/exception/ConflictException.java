package com.zcommerce.shared.exception;

import java.util.Map;

/**
 * Exception thrown when a resource conflict occurs.
 * Results in HTTP 409 Conflict responses.
 */
public class ConflictException extends ZCommerceException {
    
    public ConflictException(String message) {
        super("CONFLICT_ERROR", message);
    }
    
    public ConflictException(String message, Map<String, Object> context) {
        super("CONFLICT_ERROR", message, context);
    }
    
    public ConflictException(String message, Throwable cause) {
        super("CONFLICT_ERROR", message, cause);
    }
}