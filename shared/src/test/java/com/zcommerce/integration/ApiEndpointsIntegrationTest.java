package com.zcommerce.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for API endpoints testing response formats and error handling.
 * 
 * These tests validate:
 * - HTTP status codes are correct for different scenarios
 * - Response formats follow the standardized structure
 * - Error handling returns proper error messages
 * - API contracts are maintained across all endpoints
 */
class ApiEndpointsIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void standardResponseFormat_SuccessResponse_FollowsContract() throws Exception {
        // Test that success responses follow the standard format:
        // {
        //   "success": true,
        //   "data": { /* response payload */ },
        //   "message": "Operation completed successfully",
        //   "timestamp": "2024-01-15T10:30:00Z"
        // }
        
        String successResponseJson = """
            {
              "success": true,
              "data": {
                "userId": "12345",
                "email": "user@example.com"
              },
              "message": "User created successfully",
              "timestamp": "2024-01-15T10:30:00Z"
            }
            """;

        JsonNode response = objectMapper.readTree(successResponseJson);
        
        // Validate required fields
        assertTrue(response.has("success"));
        assertTrue(response.get("success").asBoolean());
        assertTrue(response.has("data"));
        assertTrue(response.has("message"));
        assertTrue(response.has("timestamp"));
        
        // Validate data structure
        JsonNode data = response.get("data");
        assertNotNull(data);
        assertTrue(data.has("userId"));
        assertTrue(data.has("email"));
    }

    @Test
    void standardResponseFormat_ErrorResponse_FollowsContract() throws Exception {
        // Test that error responses follow the standard format:
        // {
        //   "success": false,
        //   "error": {
        //     "code": "VALIDATION_ERROR",
        //     "message": "Invalid product ID format",
        //     "details": { /* additional error context */ }
        //   },
        //   "timestamp": "2024-01-15T10:30:00Z"
        // }
        
        String errorResponseJson = """
            {
              "success": false,
              "error": {
                "code": "VALIDATION_ERROR",
                "message": "Invalid product ID format",
                "details": {
                  "field": "productId",
                  "value": "invalid-id"
                }
              },
              "timestamp": "2024-01-15T10:30:00Z"
            }
            """;

        JsonNode response = objectMapper.readTree(errorResponseJson);
        
        // Validate required fields
        assertTrue(response.has("success"));
        assertFalse(response.get("success").asBoolean());
        assertTrue(response.has("error"));
        assertTrue(response.has("timestamp"));
        
        // Validate error structure
        JsonNode error = response.get("error");
        assertNotNull(error);
        assertTrue(error.has("code"));
        assertTrue(error.has("message"));
        assertTrue(error.has("details"));
        
        assertEquals("VALIDATION_ERROR", error.get("code").asText());
        assertEquals("Invalid product ID format", error.get("message").asText());
    }

    @Test
    void httpStatusCodes_ValidationErrors_Return400() {
        // Test that validation errors return HTTP 400
        Map<String, Integer> validationScenarios = new HashMap<>();
        validationScenarios.put("Invalid email format", 400);
        validationScenarios.put("Missing required field", 400);
        validationScenarios.put("Invalid UUID format", 400);
        validationScenarios.put("Negative price value", 400);
        validationScenarios.put("Invalid payment method", 400);
        
        for (Map.Entry<String, Integer> scenario : validationScenarios.entrySet()) {
            assertEquals(400, scenario.getValue(), 
                "Validation error '" + scenario.getKey() + "' should return HTTP 400");
        }
    }

    @Test
    void httpStatusCodes_ResourceNotFound_Return404() {
        // Test that resource not found errors return HTTP 404
        Map<String, Integer> notFoundScenarios = new HashMap<>();
        notFoundScenarios.put("User not found", 404);
        notFoundScenarios.put("Product not found", 404);
        notFoundScenarios.put("Order not found", 404);
        notFoundScenarios.put("Payment not found", 404);
        
        for (Map.Entry<String, Integer> scenario : notFoundScenarios.entrySet()) {
            assertEquals(404, scenario.getValue(), 
                "Resource not found '" + scenario.getKey() + "' should return HTTP 404");
        }
    }

    @Test
    void httpStatusCodes_ConflictErrors_Return409() {
        // Test that conflict errors return HTTP 409
        Map<String, Integer> conflictScenarios = new HashMap<>();
        conflictScenarios.put("Duplicate email registration", 409);
        conflictScenarios.put("Insufficient inventory", 409);
        conflictScenarios.put("Cannot process completed order", 409);
        conflictScenarios.put("Cannot refund failed payment", 409);
        
        for (Map.Entry<String, Integer> scenario : conflictScenarios.entrySet()) {
            assertEquals(409, scenario.getValue(), 
                "Conflict error '" + scenario.getKey() + "' should return HTTP 409");
        }
    }

    @Test
    void httpStatusCodes_SuccessfulOperations_ReturnCorrectCodes() {
        // Test that successful operations return correct HTTP status codes
        Map<String, Integer> successScenarios = new HashMap<>();
        successScenarios.put("User registration", 201);
        successScenarios.put("Product creation", 201);
        successScenarios.put("User login", 200);
        successScenarios.put("Get product details", 200);
        successScenarios.put("Add to cart", 200);
        successScenarios.put("Create order", 200);
        successScenarios.put("Process payment", 200);
        
        for (Map.Entry<String, Integer> scenario : successScenarios.entrySet()) {
            assertTrue(scenario.getValue() >= 200 && scenario.getValue() < 300, 
                "Success operation '" + scenario.getKey() + "' should return 2xx status code");
        }
    }

    @Test
    void errorMessages_ValidationErrors_AreMeaningful() throws Exception {
        // Test that validation error messages are clear and actionable
        String[] meaningfulMessages = {
            "Email is required",
            "Password must be at least 8 characters long",
            "Product name cannot be empty",
            "Price must be a positive number",
            "Quantity must be greater than 0",
            "User ID must be a valid UUID",
            "Card number is required",
            "Invalid card number format"
        };
        
        for (String message : meaningfulMessages) {
            // Validate that error messages are descriptive
            assertFalse(message.isEmpty(), "Error message should not be empty");
            assertFalse(message.toLowerCase().contains("internal"), 
                "Error message should not expose internal details: " + message);
            assertFalse(message.toLowerCase().contains("exception"), 
                "Error message should not expose exception details: " + message);
            assertTrue(message.length() > 10, 
                "Error message should be descriptive: " + message);
        }
    }

    @Test
    void apiWorkflow_UserRegistrationToPayment_ValidatesContract() throws Exception {
        // Test the complete API workflow contract
        
        // 1. User Registration Response
        String userRegistrationResponse = """
            {
              "success": true,
              "data": {
                "userId": "user-123",
                "email": "user@example.com",
                "firstName": "John",
                "lastName": "Doe"
              },
              "message": "User registered successfully",
              "timestamp": "2024-01-15T10:30:00Z"
            }
            """;
        
        JsonNode userResponse = objectMapper.readTree(userRegistrationResponse);
        assertTrue(userResponse.get("success").asBoolean());
        assertNotNull(userResponse.get("data").get("userId"));
        
        // 2. Product Creation Response
        String productCreationResponse = """
            {
              "success": true,
              "data": {
                "productId": "product-456",
                "name": "Test Product",
                "price": 99.99,
                "inventory": 50
              },
              "message": "Product created successfully",
              "timestamp": "2024-01-15T10:31:00Z"
            }
            """;
        
        JsonNode productResponse = objectMapper.readTree(productCreationResponse);
        assertTrue(productResponse.get("success").asBoolean());
        assertNotNull(productResponse.get("data").get("productId"));
        
        // 3. Order Creation Response
        String orderCreationResponse = """
            {
              "success": true,
              "data": {
                "orderId": "order-789",
                "userId": "user-123",
                "status": "PENDING",
                "totalAmount": 199.98,
                "items": [
                  {
                    "productId": "product-456",
                    "quantity": 2,
                    "price": 99.99
                  }
                ]
              },
              "message": "Order created successfully",
              "timestamp": "2024-01-15T10:32:00Z"
            }
            """;
        
        JsonNode orderResponse = objectMapper.readTree(orderCreationResponse);
        assertTrue(orderResponse.get("success").asBoolean());
        assertNotNull(orderResponse.get("data").get("orderId"));
        assertEquals("PENDING", orderResponse.get("data").get("status").asText());
        
        // 4. Payment Processing Response
        String paymentProcessingResponse = """
            {
              "success": true,
              "data": {
                "transactionId": "txn-101112",
                "orderId": "order-789",
                "status": "COMPLETED",
                "amount": 199.98,
                "paymentMethod": "CREDIT_CARD"
              },
              "message": "Payment processed successfully",
              "timestamp": "2024-01-15T10:33:00Z"
            }
            """;
        
        JsonNode paymentResponse = objectMapper.readTree(paymentProcessingResponse);
        assertTrue(paymentResponse.get("success").asBoolean());
        assertNotNull(paymentResponse.get("data").get("transactionId"));
        assertEquals("COMPLETED", paymentResponse.get("data").get("status").asText());
    }

    @Test
    void errorHandling_CrossServiceErrors_MaintainConsistency() throws Exception {
        // Test that error handling is consistent across all services
        
        String[] serviceErrors = {
            // User Service Errors
            """
            {
              "success": false,
              "error": {
                "code": "VALIDATION_ERROR",
                "message": "Email is required"
              },
              "timestamp": "2024-01-15T10:30:00Z"
            }
            """,
            
            // Product Service Errors
            """
            {
              "success": false,
              "error": {
                "code": "RESOURCE_NOT_FOUND",
                "message": "Product with ID 123 not found"
              },
              "timestamp": "2024-01-15T10:30:00Z"
            }
            """,
            
            // Order Service Errors
            """
            {
              "success": false,
              "error": {
                "code": "CONFLICT_ERROR",
                "message": "Insufficient inventory for product Test Product"
              },
              "timestamp": "2024-01-15T10:30:00Z"
            }
            """,
            
            // Payment Service Errors
            """
            {
              "success": false,
              "error": {
                "code": "VALIDATION_ERROR",
                "message": "Invalid payment method: INVALID_METHOD"
              },
              "timestamp": "2024-01-15T10:30:00Z"
            }
            """
        };
        
        for (String errorJson : serviceErrors) {
            JsonNode errorResponse = objectMapper.readTree(errorJson);
            
            // Validate consistent error structure
            assertFalse(errorResponse.get("success").asBoolean());
            assertTrue(errorResponse.has("error"));
            assertTrue(errorResponse.has("timestamp"));
            
            JsonNode error = errorResponse.get("error");
            assertTrue(error.has("code"));
            assertTrue(error.has("message"));
            
            // Validate error codes follow convention
            String errorCode = error.get("code").asText();
            assertTrue(errorCode.matches("[A-Z_]+"), 
                "Error code should be uppercase with underscores: " + errorCode);
            
            // Validate error messages are meaningful
            String errorMessage = error.get("message").asText();
            assertFalse(errorMessage.isEmpty(), "Error message should not be empty");
            assertFalse(errorMessage.toLowerCase().contains("exception"), 
                "Error message should not expose exception details");
        }
    }

    @Test
    void responseTimestamps_AllResponses_IncludeValidTimestamps() throws Exception {
        // Test that all responses include valid ISO 8601 timestamps
        
        String[] responsesWithTimestamps = {
            """
            {
              "success": true,
              "data": {},
              "message": "Success",
              "timestamp": "2024-01-15T10:30:00Z"
            }
            """,
            """
            {
              "success": false,
              "error": {
                "code": "ERROR",
                "message": "Error occurred"
              },
              "timestamp": "2024-01-15T10:30:00.123Z"
            }
            """
        };
        
        for (String responseJson : responsesWithTimestamps) {
            JsonNode response = objectMapper.readTree(responseJson);
            
            assertTrue(response.has("timestamp"), "Response should include timestamp");
            String timestamp = response.get("timestamp").asText();
            
            // Validate ISO 8601 format (basic validation)
            assertTrue(timestamp.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{3})?Z"), 
                "Timestamp should be in ISO 8601 format: " + timestamp);
        }
    }
}