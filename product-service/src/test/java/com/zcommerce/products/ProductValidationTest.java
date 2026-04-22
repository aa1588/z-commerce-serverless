package com.zcommerce.products;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zcommerce.shared.exception.ResourceNotFoundException;
import com.zcommerce.shared.exception.ValidationException;
import com.zcommerce.shared.model.Product;
import com.zcommerce.shared.repository.CartRepository;
import com.zcommerce.shared.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for product validation edge cases.
 * Tests invalid product data, negative prices, empty names, and admin authorization.
 */
class ProductValidationTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private Context context;

    private ProductHandler productHandler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        productHandler = new ProductHandler(productRepository, cartRepository);
        objectMapper = new ObjectMapper();
        
        // Mock context to return a request ID
        when(context.getAwsRequestId()).thenReturn("test-request-id");
    }

    @Test
    void createProduct_WithEmptyName_ShouldReturnValidationError() {
        // Arrange
        APIGatewayProxyRequestEvent request = createPostRequest(Map.of(
            "name", "",
            "description", "Test description",
            "price", 10.99,
            "inventory", 5,
            "category", "Electronics"
        ));

        // Act
        APIGatewayProxyResponseEvent response = productHandler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Name is required"));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_WithNullName_ShouldReturnValidationError() {
        // Arrange
        APIGatewayProxyRequestEvent request = createPostRequest(Map.of(
            "description", "Test description",
            "price", 10.99,
            "inventory", 5,
            "category", "Electronics"
        ));

        // Act
        APIGatewayProxyResponseEvent response = productHandler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Name is required"));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_WithWhitespaceOnlyName_ShouldReturnValidationError() {
        // Arrange
        APIGatewayProxyRequestEvent request = createPostRequest(Map.of(
            "name", "   ",
            "description", "Test description",
            "price", 10.99,
            "inventory", 5,
            "category", "Electronics"
        ));

        // Act
        APIGatewayProxyResponseEvent response = productHandler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Name is required"));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_WithNegativePrice_ShouldReturnValidationError() {
        // Arrange
        APIGatewayProxyRequestEvent request = createPostRequest(Map.of(
            "name", "Test Product",
            "description", "Test description",
            "price", -5.99,
            "inventory", 5,
            "category", "Electronics"
        ));

        // Act
        APIGatewayProxyResponseEvent response = productHandler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Price must be positive"));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_WithZeroPrice_ShouldReturnValidationError() {
        // Arrange
        APIGatewayProxyRequestEvent request = createPostRequest(Map.of(
            "name", "Test Product",
            "description", "Test description",
            "price", 0.0,
            "inventory", 5,
            "category", "Electronics"
        ));

        // Act
        APIGatewayProxyResponseEvent response = productHandler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Price must be positive"));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_WithNullPrice_ShouldReturnValidationError() {
        // Arrange
        APIGatewayProxyRequestEvent request = createPostRequest(Map.of(
            "name", "Test Product",
            "description", "Test description",
            "inventory", 5,
            "category", "Electronics"
        ));

        // Act
        APIGatewayProxyResponseEvent response = productHandler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Price must be positive"));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_WithNegativeInventory_ShouldReturnValidationError() {
        // Arrange
        APIGatewayProxyRequestEvent request = createPostRequest(Map.of(
            "name", "Test Product",
            "description", "Test description",
            "price", 10.99,
            "inventory", -1,
            "category", "Electronics"
        ));

        // Act
        APIGatewayProxyResponseEvent response = productHandler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Inventory must be non-negative"));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_WithEmptyDescription_ShouldReturnValidationError() {
        // Arrange
        APIGatewayProxyRequestEvent request = createPostRequest(Map.of(
            "name", "Test Product",
            "description", "",
            "price", 10.99,
            "inventory", 5,
            "category", "Electronics"
        ));

        // Act
        APIGatewayProxyResponseEvent response = productHandler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Description is required"));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_WithEmptyCategory_ShouldReturnValidationError() {
        // Arrange
        APIGatewayProxyRequestEvent request = createPostRequest(Map.of(
            "name", "Test Product",
            "description", "Test description",
            "price", 10.99,
            "inventory", 5,
            "category", ""
        ));

        // Act
        APIGatewayProxyResponseEvent response = productHandler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Category is required"));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updateProduct_WithNonExistentProduct_ShouldReturnNotFoundError() {
        // Arrange
        when(productRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        APIGatewayProxyRequestEvent request = createPutRequest("non-existent-id", Map.of(
            "name", "Updated Product"
        ));

        // Act
        APIGatewayProxyResponseEvent response = productHandler.handleRequest(request, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Product with ID non-existent-id not found"));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updateProduct_WithNegativePrice_ShouldReturnValidationError() {
        // Arrange
        Product existingProduct = createTestProduct();
        when(productRepository.findById("test-id")).thenReturn(Optional.of(existingProduct));

        APIGatewayProxyRequestEvent request = createPutRequest("test-id", Map.of(
            "price", -10.99
        ));

        // Act
        APIGatewayProxyResponseEvent response = productHandler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Price must be positive"));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updateProduct_WithNegativeInventory_ShouldReturnValidationError() {
        // Arrange
        Product existingProduct = createTestProduct();
        when(productRepository.findById("test-id")).thenReturn(Optional.of(existingProduct));

        APIGatewayProxyRequestEvent request = createPutRequest("test-id", Map.of(
            "inventory", -5
        ));

        // Act
        APIGatewayProxyResponseEvent response = productHandler.handleRequest(request, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Inventory must be non-negative"));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void deleteProduct_WithNonExistentProduct_ShouldReturnNotFoundError() {
        // Arrange
        when(productRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        APIGatewayProxyRequestEvent request = createDeleteRequest("non-existent-id");

        // Act
        APIGatewayProxyResponseEvent response = productHandler.handleRequest(request, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Product with ID non-existent-id not found"));
        verify(productRepository, never()).deleteById(anyString());
    }

    @Test
    void getProduct_WithNonExistentProduct_ShouldReturnNotFoundError() {
        // Arrange
        when(productRepository.findById("non-existent-id")).thenReturn(Optional.empty());

        APIGatewayProxyRequestEvent request = createGetRequest("non-existent-id");

        // Act
        APIGatewayProxyResponseEvent response = productHandler.handleRequest(request, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Product with ID non-existent-id not found"));
    }

    @Test
    void createProduct_WithValidData_ShouldSucceed() {
        // Arrange
        APIGatewayProxyRequestEvent request = createPostRequest(Map.of(
            "name", "Valid Product",
            "description", "Valid description",
            "price", 19.99,
            "inventory", 10,
            "category", "Electronics"
        ));

        // Act
        APIGatewayProxyResponseEvent response = productHandler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void updateProduct_WithValidData_ShouldSucceed() {
        // Arrange
        Product existingProduct = createTestProduct();
        when(productRepository.findById("test-id")).thenReturn(Optional.of(existingProduct));

        APIGatewayProxyRequestEvent request = createPutRequest("test-id", Map.of(
            "name", "Updated Product",
            "price", 29.99
        ));

        // Act
        APIGatewayProxyResponseEvent response = productHandler.handleRequest(request, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    private APIGatewayProxyRequestEvent createPostRequest(Map<String, Object> body) {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setHttpMethod("POST");
        request.setPath("/products");
        try {
            request.setBody(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return request;
    }

    private APIGatewayProxyRequestEvent createPutRequest(String productId, Map<String, Object> body) {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setHttpMethod("PUT");
        request.setPath("/products/" + productId);
        
        Map<String, String> pathParameters = new HashMap<>();
        pathParameters.put("productId", productId);
        request.setPathParameters(pathParameters);
        
        try {
            request.setBody(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return request;
    }

    private APIGatewayProxyRequestEvent createDeleteRequest(String productId) {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setHttpMethod("DELETE");
        request.setPath("/products/" + productId);
        
        Map<String, String> pathParameters = new HashMap<>();
        pathParameters.put("productId", productId);
        request.setPathParameters(pathParameters);
        
        return request;
    }

    private APIGatewayProxyRequestEvent createGetRequest(String productId) {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setHttpMethod("GET");
        request.setPath("/products/" + productId);
        
        Map<String, String> pathParameters = new HashMap<>();
        pathParameters.put("productId", productId);
        request.setPathParameters(pathParameters);
        
        return request;
    }

    private Product createTestProduct() {
        return new Product(
            "test-id",
            "Test Product",
            "Test Description",
            new BigDecimal("19.99"),
            10,
            "Electronics"
        );
    }
}