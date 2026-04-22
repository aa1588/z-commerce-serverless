package com.zcommerce.shared.logging;

import com.zcommerce.shared.exception.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Tag;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for business rule error message validation.
 * **Property 15: Business Rule Violations Return Meaningful Messages**
 * **Validates: Requirements 9.2**
 */
@Tag("Feature: z-commerce, Property 15: Business Rule Violations Return Meaningful Messages")
class BusinessRuleErrorMessagesPropertyTest {

    @Property
    void validationExceptionsShouldReturnMeaningfulMessages(
            @ForAll @AlphaChars @StringLength(min = 5, max = 50) String fieldName,
            @ForAll @AlphaChars @StringLength(min = 10, max = 100) String errorDescription) {
        
        // Create validation exception with meaningful message
        String expectedMessage = String.format("Invalid %s: %s", fieldName, errorDescription);
        ValidationException exception = new ValidationException(expectedMessage);
        
        // Verify message is meaningful and doesn't expose internal implementation details
        assertNotNull(exception.getMessage());
        assertFalse(exception.getMessage().isEmpty());
        assertTrue(exception.getMessage().contains(fieldName));
        
        // Check that the message doesn't expose internal system details
        // (but allow user-provided content to contain these words)
        String messageWithoutUserContent = exception.getMessage()
                .replace(fieldName, "")
                .replace(errorDescription, "");
        
        assertFalse(messageWithoutUserContent.contains("Exception"));
        assertFalse(messageWithoutUserContent.contains("Stack"));
        assertFalse(messageWithoutUserContent.contains("Internal"));
        assertFalse(messageWithoutUserContent.contains("DynamoDB"));
        assertFalse(messageWithoutUserContent.contains("Lambda"));
        
        assertEquals("VALIDATION_ERROR", exception.getErrorCode());
    }

    @Property
    void resourceNotFoundExceptionsShouldProvideContext(
            @ForAll("resourceTypes") String resourceType,
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String resourceId) {
        
        ResourceNotFoundException exception = new ResourceNotFoundException(resourceType, resourceId);
        
        // Verify message provides context without exposing internals
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains(resourceType));
        assertTrue(exception.getMessage().contains(resourceId));
        assertTrue(exception.getMessage().contains("not found"));
        assertFalse(exception.getMessage().contains("DynamoDB"));
        assertFalse(exception.getMessage().contains("Table"));
        assertFalse(exception.getMessage().contains("Query"));
        assertFalse(exception.getMessage().contains("Internal"));
        
        assertEquals("RESOURCE_NOT_FOUND", exception.getErrorCode());
    }

    @Property
    void conflictExceptionsShouldExplainConflict(
            @ForAll @AlphaChars @StringLength(min = 5, max = 30) String conflictReason) {
        
        ConflictException exception = new ConflictException(conflictReason);
        
        // Verify message explains the conflict clearly
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains(conflictReason));
        assertFalse(exception.getMessage().contains("Exception"));
        assertFalse(exception.getMessage().contains("Internal"));
        assertFalse(exception.getMessage().contains("System"));
        
        assertEquals("CONFLICT_ERROR", exception.getErrorCode());
    }

    @Property
    void authenticationExceptionsShouldNotExposeSecurityDetails(
            @ForAll @AlphaChars @StringLength(min = 10, max = 50) String authMessage) {
        
        AuthenticationException exception = new AuthenticationException(authMessage);
        
        // Verify message doesn't expose security internals
        assertNotNull(exception.getMessage());
        
        // Check that the message doesn't expose internal security details
        // (but allow user-provided content to contain these words)
        String messageWithoutUserContent = exception.getMessage().replace(authMessage, "");
        
        assertFalse(messageWithoutUserContent.contains("password"));
        assertFalse(messageWithoutUserContent.contains("hash"));
        assertFalse(messageWithoutUserContent.contains("token"));
        assertFalse(messageWithoutUserContent.contains("secret"));
        assertFalse(messageWithoutUserContent.contains("key"));
        assertFalse(messageWithoutUserContent.contains("Internal"));
        
        assertEquals("AUTHENTICATION_ERROR", exception.getErrorCode());
    }

    @Property
    void allBusinessExceptionsShouldHaveConsistentStructure(
            @ForAll("businessExceptions") ZCommerceException exception) {
        
        // Verify all business exceptions have consistent structure
        assertNotNull(exception.getErrorCode());
        assertFalse(exception.getErrorCode().isEmpty());
        assertTrue(exception.getErrorCode().matches("[A-Z_]+"));
        
        assertNotNull(exception.getMessage());
        assertFalse(exception.getMessage().isEmpty());
        assertFalse(exception.getMessage().contains("null"));
        assertFalse(exception.getMessage().contains("Exception"));
        assertFalse(exception.getMessage().contains("Internal"));
        
        // Verify context doesn't expose sensitive information
        Map<String, Object> context = exception.getContext();
        if (context != null) {
            context.values().forEach(value -> {
                if (value instanceof String) {
                    String stringValue = (String) value;
                    assertFalse(stringValue.contains("password"));
                    assertFalse(stringValue.contains("secret"));
                    assertFalse(stringValue.contains("key"));
                    assertFalse(stringValue.contains("token"));
                }
            });
        }
    }

    @Provide
    Arbitrary<String> resourceTypes() {
        return Arbitraries.of("User", "Product", "Order", "Cart", "Payment");
    }

    @Provide
    Arbitrary<ZCommerceException> businessExceptions() {
        return Arbitraries.oneOf(
                Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(50)
                        .map(ValidationException::new),
                Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(50)
                        .map(ConflictException::new),
                Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(50)
                        .map(AuthenticationException::new),
                Arbitraries.create(() -> new ResourceNotFoundException("Product", "test-id"))
        );
    }
}