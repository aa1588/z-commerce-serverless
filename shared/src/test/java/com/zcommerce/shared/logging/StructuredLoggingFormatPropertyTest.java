package com.zcommerce.shared.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Tag;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for structured logging format consistency.
 * **Property 16: Structured Logging Format Consistency**
 * **Validates: Requirements 9.5**
 */
@Tag("Feature: z-commerce, Property 16: Structured Logging Format Consistency")
class StructuredLoggingFormatPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Property
    void allLogEntriesShouldHaveConsistentJsonFormat(
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String serviceName,
            @ForAll @AlphaChars @StringLength(min = 5, max = 30) String event,
            @ForAll("logLevels") String level,
            @ForAll("contextMaps") Map<String, Object> context) {
        
        // Capture log output
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
        
        try {
            StructuredLogger logger = new StructuredLogger(serviceName);
            
            // Log based on level - only one log entry to avoid concatenation issues
            switch (level) {
                case "INFO" -> logger.info(event, context);
                case "WARN" -> logger.warn(event, context);
                case "DEBUG" -> logger.debug(event, context);
                case "ERROR" -> logger.error(event, context, new RuntimeException("Test exception"));
            }
            
            String logOutput = outputStream.toString().trim();
            
            // Verify JSON format
            assertNotNull(logOutput);
            assertFalse(logOutput.isEmpty());
            
            // Extract just the JSON part (after the SLF4J prefix)
            String jsonPart = extractJsonFromLogOutput(logOutput);
            
            // Parse as JSON to verify structure
            JsonNode logEntry = objectMapper.readTree(jsonPart);
            
            // Verify required fields are present
            assertTrue(logEntry.has("timestamp"));
            assertTrue(logEntry.has("level"));
            assertTrue(logEntry.has("service"));
            assertTrue(logEntry.has("requestId"));
            assertTrue(logEntry.has("event"));
            assertTrue(logEntry.has("context"));
            
            // Verify field types and values
            assertEquals(level, logEntry.get("level").asText());
            assertEquals(serviceName, logEntry.get("service").asText());
            assertEquals(event, logEntry.get("event").asText());
            
            // Verify timestamp is valid (could be ISO string or numeric)
            String timestamp = logEntry.get("timestamp").asText();
            if (timestamp.matches("\\d+(\\.\\d+)?(E\\d+)?")) {
                // Numeric timestamp - verify it's a valid number
                assertDoesNotThrow(() -> Double.parseDouble(timestamp));
            } else {
                // ISO string timestamp - verify it's parseable
                assertDoesNotThrow(() -> Instant.parse(timestamp));
            }
            
            // Verify requestId format
            String requestId = logEntry.get("requestId").asText();
            assertTrue(requestId.startsWith("req-"));
            assertTrue(requestId.matches("req-\\d+-[a-f0-9]+"));
            
            // Verify context is an object
            assertTrue(logEntry.get("context").isObject());
            
            // If error level, verify error field exists
            if ("ERROR".equals(level)) {
                assertTrue(logEntry.has("error"));
                assertTrue(logEntry.get("error").isObject());
                assertTrue(logEntry.get("error").has("message"));
                assertTrue(logEntry.get("error").has("type"));
            }
            
        } catch (Exception e) {
            fail("Failed to parse log output as JSON: " + e.getMessage());
        } finally {
            System.setOut(originalOut);
        }
    }

    @Property
    void errorLogsShouldIncludeExceptionDetails(
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String serviceName,
            @ForAll @AlphaChars @StringLength(min = 10, max = 50) String errorMessage) {
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
        
        try {
            StructuredLogger logger = new StructuredLogger(serviceName);
            RuntimeException testException = new RuntimeException(errorMessage);
            
            logger.error("Test error event", new HashMap<>(), testException);
            
            String logOutput = outputStream.toString().trim();
            String jsonPart = extractJsonFromLogOutput(logOutput);
            JsonNode logEntry = objectMapper.readTree(jsonPart);
            
            // Verify error field structure
            assertTrue(logEntry.has("error"));
            JsonNode errorNode = logEntry.get("error");
            
            assertTrue(errorNode.has("message"));
            assertTrue(errorNode.has("type"));
            
            assertEquals(errorMessage, errorNode.get("message").asText());
            assertEquals("RuntimeException", errorNode.get("type").asText());
            
        } catch (Exception e) {
            fail("Failed to parse error log: " + e.getMessage());
        } finally {
            System.setOut(originalOut);
        }
    }

    @Property
    void contextDataShouldBePreservedInJsonFormat(
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String serviceName,
            @ForAll("complexContext") Map<String, Object> context) {
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
        
        try {
            StructuredLogger logger = new StructuredLogger(serviceName);
            logger.info("Context test", context);
            
            String logOutput = outputStream.toString().trim();
            String jsonPart = extractJsonFromLogOutput(logOutput);
            JsonNode logEntry = objectMapper.readTree(jsonPart);
            
            JsonNode contextNode = logEntry.get("context");
            
            // Verify context data is preserved
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                assertTrue(contextNode.has(entry.getKey()));
                
                Object value = entry.getValue();
                if (value instanceof String) {
                    assertEquals(value, contextNode.get(entry.getKey()).asText());
                } else if (value instanceof Integer) {
                    assertEquals(value, contextNode.get(entry.getKey()).asInt());
                } else if (value instanceof Boolean) {
                    assertEquals(value, contextNode.get(entry.getKey()).asBoolean());
                }
            }
            
        } catch (Exception e) {
            fail("Failed to parse context log: " + e.getMessage());
        } finally {
            System.setOut(originalOut);
        }
    }

    @Property
    void loggersShouldMaintainConsistentRequestId(
            @ForAll @AlphaChars @StringLength(min = 5, max = 20) String serviceName) {
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
        
        try {
            StructuredLogger logger = new StructuredLogger(serviceName);
            
            // Log two different events with same logger
            logger.info("first_event", new HashMap<>());
            logger.warn("second_event", new HashMap<>());
            
            String logOutput = outputStream.toString().trim();
            String[] logLines = logOutput.split("\n");
            
            // Should have at least 2 log lines
            assertTrue(logLines.length >= 2);
            
            String firstJsonPart = extractJsonFromLogOutput(logLines[0]);
            String secondJsonPart = extractJsonFromLogOutput(logLines[1]);
            
            JsonNode firstEntry = objectMapper.readTree(firstJsonPart);
            JsonNode secondEntry = objectMapper.readTree(secondJsonPart);
            
            // Verify same service name
            assertEquals(serviceName, firstEntry.get("service").asText());
            assertEquals(serviceName, secondEntry.get("service").asText());
            
            // Verify same requestId (same logger instance)
            String firstRequestId = firstEntry.get("requestId").asText();
            String secondRequestId = secondEntry.get("requestId").asText();
            assertEquals(firstRequestId, secondRequestId);
            
        } catch (Exception e) {
            fail("Failed to parse log output: " + e.getMessage());
        } finally {
            System.setOut(originalOut);
        }
    }

    /**
     * Extract JSON part from SLF4J log output.
     * SLF4J output format: "timestamp [thread] LEVEL logger -- {json}"
     */
    private String extractJsonFromLogOutput(String logOutput) {
        int jsonStart = logOutput.indexOf('{');
        if (jsonStart == -1) {
            return logOutput; // Fallback if no JSON found
        }
        return logOutput.substring(jsonStart);
    }

    @Provide
    Arbitrary<String> logLevels() {
        return Arbitraries.of("INFO", "WARN", "DEBUG", "ERROR");
    }

    @Provide
    Arbitrary<Map<String, Object>> contextMaps() {
        return Arbitraries.maps(
                Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10),
                Arbitraries.oneOf(
                        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20).map(s -> (Object) s),
                        Arbitraries.integers().between(1, 1000).map(i -> (Object) i),
                        Arbitraries.of(true, false).map(b -> (Object) b)
                )
        ).ofMinSize(0).ofMaxSize(5);
    }

    @Provide
    Arbitrary<Map<String, Object>> complexContext() {
        return Arbitraries.maps(
                Arbitraries.of("userId", "orderId", "productId", "amount", "status"),
                Arbitraries.oneOf(
                        Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15).map(s -> (Object) s),
                        Arbitraries.integers().between(1, 10000).map(i -> (Object) i),
                        Arbitraries.of(true, false).map(b -> (Object) b)
                )
        ).ofMinSize(1).ofMaxSize(3);
    }
}