package com.zcommerce.shared.exception;

import java.util.Map;

/**
 * Exception thrown when input validation fails.
 * Results in HTTP 400 Bad Request responses.
 */
public class ValidationException extends ZCommerceException {
    
    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }
    
    public ValidationException(String message, Map<String, Object> context) {
        super("VALIDATION_ERROR", message, context);
    }
    
    public ValidationException(String message, Throwable cause) {
        super("VALIDATION_ERROR", message, cause);
    }
}