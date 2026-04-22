package com.zcommerce.cart;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zcommerce.shared.model.CartItem;
import com.zcommerce.shared.model.Product;
import com.zcommerce.shared.repository.CartRepository;
import com.zcommerce.shared.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CartHandler edge cases including insufficient inventory,
 * invalid product IDs, and boundary values.
 */
class CartEdgeCasesTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private Context context;

    private CartHandler cartHandler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        cartHandler = new CartHandler(cartRepository, productRepository);
        objectMapper = new ObjectMapper();
    }

    @Test
    void addItem_WithInsufficientInventory_Returns409() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        
        Product product = new Product(productId, "Test Product", "Description", new BigDecimal("10.00"), 5, "Electronics");
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        CartHandler.AddItemRequest request = new CartHandler.AddItemRequest();
        request.productId = productId;
        request.quantity = 10; // More than available inventory (5)

        APIGatewayProxyRequestEvent event = createAddItemRequest(userId, request);

        // Act
        APIGatewayProxyResponseEvent response = cartHandler.handleRequest(event, context);

        // Assert
        assertEquals(409, response.getStatusCode());
        assertTrue(response.getBody().contains("Insufficient inventory"));
        assertTrue(response.getBody().contains("Available: 5"));
        assertTrue(response.getBody().contains("Requested: 10"));
        verify(cartRepository, never()).save(any());
    }

    @Test
    void addItem_WithInvalidProductId_Returns404() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        String invalidProductId = UUID.randomUUID().toString();
        
        when(productRepository.findById(invalidProductId)).thenReturn(Optional.empty());

        CartHandler.AddItemRequest request = new CartHandler.AddItemRequest();
        request.productId = invalidProductId;
        request.quantity = 1;

        APIGatewayProxyRequestEvent event = createAddItemRequest(userId, request);

        // Act
        APIGatewayProxyResponseEvent response = cartHandler.handleRequest(event, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Product"));
        assertTrue(response.getBody().contains("not found"));
        verify(cartRepository, never()).save(any());
    }

    @Test
    void addItem_WithZeroQuantity_Returns400() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();

        CartHandler.AddItemRequest request = new CartHandler.AddItemRequest();
        request.productId = productId;
        request.quantity = 0; // Invalid quantity

        APIGatewayProxyRequestEvent event = createAddItemRequest(userId, request);

        // Act
        APIGatewayProxyResponseEvent response = cartHandler.handleRequest(event, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Quantity"));
        verify(productRepository, never()).findById(any());
        verify(cartRepository, never()).save(any());
    }

    @Test
    void addItem_WithNegativeQuantity_Returns400() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();

        CartHandler.AddItemRequest request = new CartHandler.AddItemRequest();
        request.productId = productId;
        request.quantity = -5; // Invalid negative quantity

        APIGatewayProxyRequestEvent event = createAddItemRequest(userId, request);

        // Act
        APIGatewayProxyResponseEvent response = cartHandler.handleRequest(event, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Quantity"));
        verify(productRepository, never()).findById(any());
        verify(cartRepository, never()).save(any());
    }

    @Test
    void addItem_ExistingItemExceedsInventory_Returns409() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        
        Product product = new Product(productId, "Test Product", "Description", new BigDecimal("10.00"), 10, "Electronics");
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        // Existing cart item with quantity 8
        CartItem existingItem = new CartItem(userId, productId, 8);
        when(cartRepository.findByUserIdAndProductId(userId, productId)).thenReturn(Optional.of(existingItem));

        CartHandler.AddItemRequest request = new CartHandler.AddItemRequest();
        request.productId = productId;
        request.quantity = 5; // Would make total 13, exceeding inventory of 10

        APIGatewayProxyRequestEvent event = createAddItemRequest(userId, request);

        // Act
        APIGatewayProxyResponseEvent response = cartHandler.handleRequest(event, context);

        // Assert
        assertEquals(409, response.getStatusCode());
        assertTrue(response.getBody().contains("Insufficient inventory"));
        assertTrue(response.getBody().contains("Available: 10"));
        assertTrue(response.getBody().contains("Requested total: 13"));
        verify(cartRepository, never()).save(any());
    }

    @Test
    void updateItem_WithExcessiveQuantity_Returns409() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        
        Product product = new Product(productId, "Test Product", "Description", new BigDecimal("10.00"), 5, "Electronics");
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        CartItem existingItem = new CartItem(userId, productId, 2);
        when(cartRepository.findByUserIdAndProductId(userId, productId)).thenReturn(Optional.of(existingItem));

        CartHandler.UpdateItemRequest request = new CartHandler.UpdateItemRequest();
        request.quantity = 10; // Exceeds inventory of 5

        APIGatewayProxyRequestEvent event = createUpdateItemRequest(userId, productId, request);

        // Act
        APIGatewayProxyResponseEvent response = cartHandler.handleRequest(event, context);

        // Assert
        assertEquals(409, response.getStatusCode());
        assertTrue(response.getBody().contains("Insufficient inventory"));
        verify(cartRepository, never()).save(any());
    }

    @Test
    void updateItem_NonExistentCartItem_Returns404() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        
        when(cartRepository.findByUserIdAndProductId(userId, productId)).thenReturn(Optional.empty());

        CartHandler.UpdateItemRequest request = new CartHandler.UpdateItemRequest();
        request.quantity = 3;

        APIGatewayProxyRequestEvent event = createUpdateItemRequest(userId, productId, request);

        // Act
        APIGatewayProxyResponseEvent response = cartHandler.handleRequest(event, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Cart item"));
        assertTrue(response.getBody().contains("not found"));
        verify(cartRepository, never()).save(any());
    }

    @Test
    void updateItem_ProductNoLongerExists_Returns404() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        
        CartItem existingItem = new CartItem(userId, productId, 2);
        when(cartRepository.findByUserIdAndProductId(userId, productId)).thenReturn(Optional.of(existingItem));
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        CartHandler.UpdateItemRequest request = new CartHandler.UpdateItemRequest();
        request.quantity = 3;

        APIGatewayProxyRequestEvent event = createUpdateItemRequest(userId, productId, request);

        // Act
        APIGatewayProxyResponseEvent response = cartHandler.handleRequest(event, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Product"));
        assertTrue(response.getBody().contains("not found"));
        verify(cartRepository, never()).save(any());
    }

    @Test
    void removeItem_NonExistentCartItem_Returns404() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        
        when(cartRepository.findByUserIdAndProductId(userId, productId)).thenReturn(Optional.empty());

        APIGatewayProxyRequestEvent event = createRemoveItemRequest(userId, productId);

        // Act
        APIGatewayProxyResponseEvent response = cartHandler.handleRequest(event, context);

        // Assert
        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("Cart item"));
        assertTrue(response.getBody().contains("not found"));
        verify(cartRepository, never()).deleteByUserIdAndProductId(any(), any());
    }

    @Test
    void addItem_WithMaxIntegerQuantity_HandledCorrectly() throws Exception {
        // Arrange
        String userId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        
        Product product = new Product(productId, "Test Product", "Description", new BigDecimal("10.00"), Integer.MAX_VALUE, "Electronics");
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(cartRepository.findByUserIdAndProductId(userId, productId)).thenReturn(Optional.empty());

        CartHandler.AddItemRequest request = new CartHandler.AddItemRequest();
        request.productId = productId;
        request.quantity = Integer.MAX_VALUE;

        APIGatewayProxyRequestEvent event = createAddItemRequest(userId, request);

        // Act
        APIGatewayProxyResponseEvent response = cartHandler.handleRequest(event, context);

        // Assert
        assertEquals(200, response.getStatusCode());
        verify(cartRepository).save(any(CartItem.class));
    }

    @Test
    void addItem_WithInvalidUserId_Returns400() throws Exception {
        // Arrange
        String invalidUserId = "not-a-uuid";
        String productId = UUID.randomUUID().toString();

        CartHandler.AddItemRequest request = new CartHandler.AddItemRequest();
        request.productId = productId;
        request.quantity = 1;

        APIGatewayProxyRequestEvent event = createAddItemRequest(invalidUserId, request);

        // Act
        APIGatewayProxyResponseEvent response = cartHandler.handleRequest(event, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("User ID"));
        verify(productRepository, never()).findById(any());
        verify(cartRepository, never()).save(any());
    }

    @Test
    void getCart_WithInvalidUserId_Returns400() throws Exception {
        // Arrange
        String invalidUserId = "not-a-uuid";
        APIGatewayProxyRequestEvent event = createGetCartRequest(invalidUserId);

        // Act
        APIGatewayProxyResponseEvent response = cartHandler.handleRequest(event, context);

        // Assert
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("User ID"));
        verify(cartRepository, never()).findByUserId(any());
    }

    // Helper methods to create test requests

    private APIGatewayProxyRequestEvent createAddItemRequest(String userId, CartHandler.AddItemRequest request) throws Exception {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setHttpMethod("POST");
        event.setPath("/cart/" + userId + "/items");
        
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("userId", userId);
        event.setPathParameters(pathParams);
        
        event.setBody(objectMapper.writeValueAsString(request));
        return event;
    }

    private APIGatewayProxyRequestEvent createUpdateItemRequest(String userId, String productId, CartHandler.UpdateItemRequest request) throws Exception {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setHttpMethod("PUT");
        event.setPath("/cart/" + userId + "/items/" + productId);
        
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("userId", userId);
        pathParams.put("productId", productId);
        event.setPathParameters(pathParams);
        
        event.setBody(objectMapper.writeValueAsString(request));
        return event;
    }

    private APIGatewayProxyRequestEvent createRemoveItemRequest(String userId, String productId) {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setHttpMethod("DELETE");
        event.setPath("/cart/" + userId + "/items/" + productId);
        
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("userId", userId);
        pathParams.put("productId", productId);
        event.setPathParameters(pathParams);
        
        return event;
    }

    private APIGatewayProxyRequestEvent createGetCartRequest(String userId) {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setHttpMethod("GET");
        event.setPath("/cart/" + userId);
        
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("userId", userId);
        event.setPathParameters(pathParams);
        
        return event;
    }
}