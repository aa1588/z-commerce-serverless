package com.zcommerce.shared.exception;

import java.util.Map;

/**
 * Exception thrown when a requested resource is not found.
 * Results in HTTP 404 Not Found responses.
 */
public class ResourceNotFoundException extends ZCommerceException {
    
    public ResourceNotFoundException(String resourceType, String resourceId) {
        super("RESOURCE_NOT_FOUND", 
              String.format("%s with ID %s not found", resourceType, resourceId));
        addContext("resourceType", resourceType);
        addContext("resourceId", resourceId);
    }
    
    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message);
    }
    
    public ResourceNotFoundException(String message, Map<String, Object> context) {
        super("RESOURCE_NOT_FOUND", message, context);
    }
}