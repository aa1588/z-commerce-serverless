package com.zcommerce.shared.persistence;

import com.zcommerce.shared.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to verify referential integrity concepts work.
 * This validates the same concepts as the property test but with concrete examples.
 */
class SimpleReferentialIntegrityTest {

    @Test
    void testCartItemsReferenceValidProducts() {
        String userId = "user123";
        String productId = "prod456";
        
        // Create a product first
        Product product = new Product(
            productId, 
            "Test Product", 
            "Test Description", 
            BigDecimal.valueOf(10.00), 
            100, 
            "Electronics"
        );
        
        // Create cart item that references the product
        CartItem cartItem = new CartItem(userId, productId, 2);
        
        // Verify referential integrity: cart item references existing product
        assertEquals(productId, cartItem.getProductId());
        assertEquals(productId, product.getProductId());
        
        // Verify key structure maintains relationship
        assertTrue(cartItem.getSk().contains(productId));
        assertEquals("CART#PRODUCT#" + productId, cartItem.getSk());
        
        // Verify entity types are consistent
        assertEquals("CART_ITEM", cartItem.getEntityType());
        assertEquals("PRODUCT", product.getEntityType());
    }

    @Test
    void testOrderItemsReferenceValidProducts() {
        String orderId = "order789";
        String userId = "user123";
        String productId = "prod456";
        BigDecimal price = BigDecimal.valueOf(25.50);
        
        // Create product
        Product product = new Product(
            productId,
            "Test Product",
            "Test Description", 
            price,
            100,
            "Electronics"
        );
        
        // Create order item that references the product
        OrderItem orderItem = new OrderItem(productId, product.getName(), 2, price);
        
        // Create order containing the order item
        Order order = new Order(orderId, userId, Arrays.asList(orderItem), price.multiply(BigDecimal.valueOf(2)));
        
        // Verify referential integrity: order item references existing product
        assertEquals(productId, orderItem.getProductId());
        assertEquals(product.getName(), orderItem.getProductName());
        assertEquals(price, orderItem.getPrice());
        
        // Verify order contains the item
        assertTrue(order.getItems().contains(orderItem));
        assertEquals(1, order.getItems().size());
        
        // Verify key structures maintain relationships
        assertEquals("ORDER#" + orderId, order.getPk());
        assertEquals("ORDER#" + userId, order.getGsi1pk());
        
        // Verify entity types are consistent
        assertEquals("ORDER", order.getEntityType());
        assertEquals("PRODUCT", product.getEntityType());
    }

    @Test
    void testPaymentsReferenceValidOrders() {
        String transactionId = "txn999";
        String orderId = "order789";
        String userId = "user123";
        BigDecimal amount = BigDecimal.valueOf(51.00);
        
        // Create order first
        OrderItem orderItem = new OrderItem("prod1", "Product 1", 2, BigDecimal.valueOf(25.50));
        Order order = new Order(orderId, userId, Arrays.asList(orderItem), amount);
        
        // Create payment that references the order
        Payment payment = new Payment(transactionId, orderId, amount, Payment.PaymentMethod.CREDIT_CARD);
        
        // Verify referential integrity: payment references existing order
        assertEquals(orderId, payment.getOrderId());
        assertEquals(orderId, order.getOrderId());
        assertEquals(amount, payment.getAmount());
        assertEquals(amount, order.getTotalAmount());
        
        // Verify key structures maintain relationships
        assertEquals("PAYMENT#" + transactionId, payment.getPk());
        assertEquals("ORDER#" + orderId, order.getPk());
        
        // Verify entity types are consistent
        assertEquals("PAYMENT", payment.getEntityType());
        assertEquals("ORDER", order.getEntityType());
    }

    @Test
    void testKeyStructuresPreserveRelationships() {
        String userId = "user123";
        String productId = "prod456";
        String orderId = "order789";
        String transactionId = "txn999";
        
        // Create entities with relationships
        User user = new User(userId, "test@example.com", "password", "John", "Doe");
        Product product = new Product(productId, "Product", "Description", BigDecimal.valueOf(10.00), 100, "Category");
        CartItem cartItem = new CartItem(userId, productId, 1);
        OrderItem orderItem = new OrderItem(productId, product.getName(), 1, product.getPrice());
        Order order = new Order(orderId, userId, Arrays.asList(orderItem), product.getPrice());
        Payment payment = new Payment(transactionId, orderId, product.getPrice(), Payment.PaymentMethod.CREDIT_CARD);
        
        // Verify all key structures preserve relationships correctly
        
        // User keys
        assertEquals("USER#" + userId, user.getPk());
        assertEquals("PROFILE", user.getSk());
        assertEquals("USER", user.getGsi1pk());
        assertEquals(user.getEmail(), user.getGsi1sk());
        
        // Product keys
        assertEquals("PRODUCT#" + productId, product.getPk());
        assertEquals("DETAILS", product.getSk());
        assertEquals("PRODUCT", product.getGsi1pk());
        
        // Cart item keys (links user and product)
        assertEquals("USER#" + userId, cartItem.getPk());
        assertEquals("CART#PRODUCT#" + productId, cartItem.getSk());
        
        // Order keys (links to user)
        assertEquals("ORDER#" + orderId, order.getPk());
        assertEquals("DETAILS", order.getSk());
        assertEquals("ORDER#" + userId, order.getGsi1pk());
        
        // Payment keys (links to order)
        assertEquals("PAYMENT#" + transactionId, payment.getPk());
        assertEquals("TRANSACTION", payment.getSk());
        
        // Verify relationship consistency through IDs
        assertEquals(userId, cartItem.getUserId());
        assertEquals(productId, cartItem.getProductId());
        assertEquals(userId, order.getUserId());
        assertEquals(productId, orderItem.getProductId());
        assertEquals(orderId, payment.getOrderId());
    }
}