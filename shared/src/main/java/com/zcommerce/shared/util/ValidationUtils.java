package com.zcommerce.shared.util;

import com.zcommerce.shared.exception.ValidationException;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Utility class for common validation operations across Z-Commerce services.
 */
public class ValidationUtils {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    /**
     * Validate that a string is not null or empty
     */
    public static void validateRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(fieldName + " is required");
        }
    }

    /**
     * Validate email format
     */
    public static void validateEmail(String email) {
        validateRequired(email, "Email");
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ValidationException("Invalid email format");
        }
    }

    /**
     * Validate UUID format
     */
    public static void validateUUID(String uuid, String fieldName) {
        validateRequired(uuid, fieldName);
        if (!UUID_PATTERN.matcher(uuid).matches()) {
            throw new ValidationException(fieldName + " must be a valid UUID");
        }
    }

    /**
     * Validate positive number
     */
    public static void validatePositive(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(fieldName + " must be positive");
        }
    }

    /**
     * Validate positive integer
     */
    public static void validatePositive(Integer value, String fieldName) {
        if (value == null || value <= 0) {
            throw new ValidationException(fieldName + " must be positive");
        }
    }

    /**
     * Validate non-negative integer
     */
    public static void validateNonNegative(Integer value, String fieldName) {
        if (value == null || value < 0) {
            throw new ValidationException(fieldName + " must be non-negative");
        }
    }

    /**
     * Validate string length
     */
    public static void validateLength(String value, String fieldName, int minLength, int maxLength) {
        validateRequired(value, fieldName);
        if (value.length() < minLength || value.length() > maxLength) {
            throw new ValidationException(
                String.format("%s must be between %d and %d characters", fieldName, minLength, maxLength)
            );
        }
    }

    /**
     * Validate password strength
     */
    public static void validatePassword(String password) {
        validateRequired(password, "Password");
        if (password.length() < 8) {
            throw new ValidationException("Password must be at least 8 characters long");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new ValidationException("Password must contain at least one uppercase letter");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new ValidationException("Password must contain at least one lowercase letter");
        }
        if (!password.matches(".*\\d.*")) {
            throw new ValidationException("Password must contain at least one digit");
        }
    }
}