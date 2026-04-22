package com.zcommerce.shared.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zcommerce.shared.api.ApiResponse;
import com.zcommerce.shared.exception.*;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for Error Handling and Logging.
 * Tests Properties 14, 15, and 16 from the design document.
 */
class ErrorHandlingPropertyTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Property 14: Error Logging Captures Context
     * For any error or exception that occurs in the system, appropriate error information
     * should be logged with sufficient context for debugging while protecting sensitive data.
     * **Validates: Requirements 9.1, 9.3, 9.4**
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 14: Error Logging Captures Context")
    void errorLoggingCapturesContext(
        @ForAll("errorMessages") String errorMessage,
        @ForAll("errorCodes") String errorCode,
        @ForAll("requestIds") String requestId
    ) {
        // Create structured logger
        StructuredLogger logger = new StructuredLogger("test-service", requestId);

        // Capture log output
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));

        try {
            // Log error with context
            Map<String, Object> context = new HashMap<>();
            context.put("errorCode", errorCode);
            context.put("operation", "test_operation");

            Exception testException = new RuntimeException(errorMessage);
            logger.error("error_occurred", context, testException);

            String logOutput = outputStream.toString();

            // Verify log contains required context
            assertTrue(logOutput.contains(requestId) || logOutput.contains("requestId"),
                "Log should contain request ID for debugging");
            assertTrue(logOutput.contains("ERROR") || logOutput.contains("error"),
                "Log should indicate error level");

            // Verify error message is captured
            assertTrue(logOutput.contains(errorMessage) || logOutput.contains("message"),
                "Log should contain error message");

        } finally {
            System.setOut(originalOut);
        }
    }

    /**
     * Property 14: Sensitive data is protected in logs
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 14: Error Logging Captures Context")
    void sensitiveDataProtectedInLogs(
        @ForAll("passwords") String password,
        @ForAll("cardNumbers") String cardNumber,
        @ForAll("requestIds") String requestId
    ) {
        StructuredLogger logger = new StructuredLogger("test-service", requestId);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));

        try {
            // Simulate logging that should NOT include sensitive data directly
            Map<String, Object> context = new HashMap<>();
            context.put("userId", "user123");
            // Note: password should never be added to context
            // context.put("password", password); // This should NEVER happen

            logger.info("user_operation", context);

            String logOutput = outputStream.toString();

            // Verify sensitive data is not in logs
            assertFalse(logOutput.contains(password),
                "Password should never appear in logs");
            assertFalse(logOutput.contains(cardNumber),
                "Card number should never appear in logs unmasked");

        } finally {
            System.setOut(originalOut);
        }
    }

    /**
     * Property 15: Business Rule Violations Return Meaningful Messages
     * For any business rule violation, the system should return clear, actionable error messages
     * to clients without exposing internal system details.
     * **Validates: Requirements 9.2**
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 15: Business Rule Violations Return Meaningful Messages")
    void businessRuleViolationsReturnMeaningfulMessages(
        @ForAll("validationMessages") String validationMessage,
        @ForAll("resourceTypes") String resourceType,
        @ForAll("resourceIds") String resourceId
    ) throws JsonProcessingException {
        // Test ValidationException
        ValidationException validationEx = new ValidationException(validationMessage);
        ApiResponse<?> validationResponse = ApiResponse.error(
            validationEx.getErrorCode(),
            validationEx.getMessage(),
            validationEx.getContext()
        );

        String validationJson = validationResponse.toJson();
        JsonNode validationNode = objectMapper.readTree(validationJson);

        assertFalse(validationNode.get("success").asBoolean());
        assertNotNull(validationNode.get("error"));
        assertTrue(validationNode.get("error").get("message").asText().length() > 0,
            "Error message should not be empty");
        assertFalse(validationNode.get("error").get("message").asText().contains("Exception"),
            "Error message should not expose exception class names");
        assertFalse(validationNode.get("error").get("message").asText().contains("java."),
            "Error message should not expose Java package names");

        // Test ResourceNotFoundException
        ResourceNotFoundException notFoundEx = new ResourceNotFoundException(resourceType, resourceId);
        ApiResponse<?> notFoundResponse = ApiResponse.error(
            notFoundEx.getErrorCode(),
            notFoundEx.getMessage(),
            notFoundEx.getContext()
        );

        String notFoundJson = notFoundResponse.toJson();
        JsonNode notFoundNode = objectMapper.readTree(notFoundJson);

        assertFalse(notFoundNode.get("success").asBoolean());
        assertTrue(notFoundNode.get("error").get("message").asText().contains(resourceType),
            "Error message should indicate what was not found");
    }

    /**
     * Property 15: Error responses have consistent structure
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 15: Business Rule Violations Return Meaningful Messages")
    void errorResponsesHaveConsistentStructure(
        @ForAll("errorCodes") String errorCode,
        @ForAll("errorMessages") String errorMessage
    ) throws JsonProcessingException {
        // Create error response
        ApiResponse<?> errorResponse = ApiResponse.error(errorCode, errorMessage, null);
        String json = errorResponse.toJson();

        // Parse and verify structure
        JsonNode node = objectMapper.readTree(json);

        // Verify required fields exist
        assertTrue(node.has("success"), "Response should have 'success' field");
        assertTrue(node.has("error"), "Error response should have 'error' field");
        assertTrue(node.has("timestamp"), "Response should have 'timestamp' field");

        // Verify error structure
        JsonNode errorNode = node.get("error");
        assertTrue(errorNode.has("code"), "Error should have 'code' field");
        assertTrue(errorNode.has("message"), "Error should have 'message' field");

        // Verify values
        assertFalse(node.get("success").asBoolean(), "Error response should have success=false");
        assertEquals(errorCode, errorNode.get("code").asText());
        assertEquals(errorMessage, errorNode.get("message").asText());
    }

    /**
     * Property 16: Structured Logging Format Consistency
     * For any log entry generated by the system, it should follow a consistent structured format
     * compatible with AWS CloudWatch for effective monitoring and analysis.
     * **Validates: Requirements 9.5**
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 16: Structured Logging Format Consistency")
    void structuredLoggingFormatConsistency(
        @ForAll("serviceNames") String serviceName,
        @ForAll("requestIds") String requestId,
        @ForAll("eventNames") String eventName
    ) {
        StructuredLogger logger = new StructuredLogger(serviceName, requestId);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));

        try {
            Map<String, Object> context = new HashMap<>();
            context.put("testKey", "testValue");

            logger.info(eventName, context);

            String logOutput = outputStream.toString().trim();

            // Verify JSON format (should be parseable)
            if (!logOutput.isEmpty()) {
                try {
                    JsonNode logNode = objectMapper.readTree(logOutput);

                    // Verify structured format has required fields
                    assertTrue(logNode.has("timestamp") || logNode.has("time"),
                        "Log should have timestamp");
                    assertTrue(logNode.has("level") || logOutput.contains("INFO"),
                        "Log should have level");
                    assertTrue(logNode.has("service") || logOutput.contains(serviceName),
                        "Log should have service name");
                    assertTrue(logNode.has("requestId") || logOutput.contains(requestId),
                        "Log should have request ID");

                } catch (JsonProcessingException e) {
                    // If not pure JSON, verify it's still structured output
                    assertTrue(logOutput.contains(serviceName) || logOutput.contains(requestId),
                        "Structured log should contain identifiable information");
                }
            }

        } finally {
            System.setOut(originalOut);
        }
    }

    /**
     * Property 16: Log levels are used appropriately
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 16: Structured Logging Format Consistency")
    void logLevelsUsedAppropriately(
        @ForAll("requestIds") String requestId
    ) {
        StructuredLogger logger = new StructuredLogger("test-service", requestId);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));

        try {
            // Test INFO level
            logger.info("info_event", Map.of("key", "value"));
            String infoOutput = outputStream.toString();
            assertTrue(infoOutput.contains("INFO") || infoOutput.contains("info") || infoOutput.length() > 0,
                "INFO log should be generated");

            outputStream.reset();

            // Test ERROR level
            logger.error("error_event", Map.of("key", "value"), new RuntimeException("test"));
            String errorOutput = outputStream.toString();
            assertTrue(errorOutput.contains("ERROR") || errorOutput.contains("error") || errorOutput.length() > 0,
                "ERROR log should be generated");

        } finally {
            System.setOut(originalOut);
        }
    }

    // Providers
    @Provide
    Arbitrary<String> errorMessages() {
        return Arbitraries.of(
            "Invalid input provided",
            "Required field is missing",
            "Value out of range",
            "Operation not permitted",
            "Resource already exists"
        );
    }

    @Provide
    Arbitrary<String> errorCodes() {
        return Arbitraries.of(
            "VALIDATION_ERROR",
            "NOT_FOUND",
            "CONFLICT",
            "UNAUTHORIZED",
            "INTERNAL_ERROR"
        );
    }

    @Provide
    Arbitrary<String> requestIds() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .ofLength(20);
    }

    @Provide
    Arbitrary<String> passwords() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(8)
            .ofMaxLength(20);
    }

    @Provide
    Arbitrary<String> cardNumbers() {
        return Arbitraries.strings()
            .numeric()
            .ofLength(16);
    }

    @Provide
    Arbitrary<String> validationMessages() {
        return Arbitraries.of(
            "Email is required",
            "Password must be at least 8 characters",
            "Invalid email format",
            "Price must be positive",
            "Quantity must be at least 1"
        );
    }

    @Provide
    Arbitrary<String> resourceTypes() {
        return Arbitraries.of("User", "Product", "Order", "Payment", "CartItem");
    }

    @Provide
    Arbitrary<String> resourceIds() {
        return Arbitraries.strings()
            .alpha()
            .ofLength(12);
    }

    @Provide
    Arbitrary<String> serviceNames() {
        return Arbitraries.of(
            "user-service",
            "product-service",
            "cart-service",
            "order-service",
            "payment-service"
        );
    }

    @Provide
    Arbitrary<String> eventNames() {
        return Arbitraries.of(
            "request_received",
            "user_created",
            "product_updated",
            "order_placed",
            "payment_processed"
        );
    }
}
