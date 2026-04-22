package com.zcommerce.shared.dynamodb;

/**
 * Utility class for building DynamoDB partition and sort keys following single-table design patterns.
 * Provides consistent key formatting across all entities in the Z-Commerce application.
 */
public class KeyBuilder {
    
    // Entity type prefixes
    public static final String USER_PREFIX = "USER#";
    public static final String PRODUCT_PREFIX = "PRODUCT#";
    public static final String ORDER_PREFIX = "ORDER#";
    public static final String PAYMENT_PREFIX = "PAYMENT#";
    
    // Sort key suffixes
    public static final String PROFILE_SUFFIX = "PROFILE";
    public static final String DETAILS_SUFFIX = "DETAILS";
    public static final String TRANSACTION_SUFFIX = "TRANSACTION";
    public static final String CART_PREFIX = "CART#";

    /**
     * Build user partition key
     */
    public static String userPK(String userId) {
        return USER_PREFIX + userId;
    }

    /**
     * Build user profile sort key
     */
    public static String userProfileSK() {
        return PROFILE_SUFFIX;
    }

    /**
     * Build cart item sort key
     */
    public static String cartItemSK(String productId) {
        return CART_PREFIX + PRODUCT_PREFIX + productId;
    }

    /**
     * Build product partition key
     */
    public static String productPK(String productId) {
        return PRODUCT_PREFIX + productId;
    }

    /**
     * Build product details sort key
     */
    public static String productDetailsSK() {
        return DETAILS_SUFFIX;
    }

    /**
     * Build order partition key
     */
    public static String orderPK(String orderId) {
        return ORDER_PREFIX + orderId;
    }

    /**
     * Build order details sort key
     */
    public static String orderDetailsSK() {
        return DETAILS_SUFFIX;
    }

    /**
     * Build payment partition key
     */
    public static String paymentPK(String transactionId) {
        return PAYMENT_PREFIX + transactionId;
    }

    /**
     * Build payment transaction sort key
     */
    public static String paymentTransactionSK() {
        return TRANSACTION_SUFFIX;
    }

    /**
     * Build GSI1 partition key for entity type queries
     */
    public static String gsi1PK(String entityType) {
        return entityType;
    }

    /**
     * Build GSI1 partition key with additional context (e.g., ORDER#userId)
     */
    public static String gsi1PKWithContext(String entityType, String context) {
        return entityType + "#" + context;
    }

    /**
     * Extract entity ID from partition key
     */
    public static String extractId(String partitionKey) {
        int hashIndex = partitionKey.indexOf('#');
        return hashIndex >= 0 ? partitionKey.substring(hashIndex + 1) : partitionKey;
    }

    /**
     * Extract entity type from partition key
     */
    public static String extractEntityType(String partitionKey) {
        int hashIndex = partitionKey.indexOf('#');
        return hashIndex >= 0 ? partitionKey.substring(0, hashIndex) : partitionKey;
    }
}