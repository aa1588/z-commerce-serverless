package com.zcommerce.shared.exception;

import java.util.Map;

/**
 * Exception thrown when authentication fails.
 * Results in HTTP 401 Unauthorized responses.
 */
public class AuthenticationException extends ZCommerceException {
    
    public AuthenticationException(String message) {
        super("AUTHENTICATION_ERROR", message);
    }
    
    public AuthenticationException(String message, Map<String, Object> context) {
        super("AUTHENTICATION_ERROR", message, context);
    }
    
    public AuthenticationException(String message, Throwable cause) {
        super("AUTHENTICATION_ERROR", message, cause);
    }
}