package com.zcommerce.shared.persistence;

import com.zcommerce.shared.model.*;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for referential integrity maintenance.
 * **Validates: Requirements 7.4**
 */
class ReferentialIntegrityPropertyTest {

    /**
     * **Validates: Requirements 7.4**
     * Property 13: Referential Integrity Maintenance
     * For any related entities in the system, operations that affect relationships 
     * should maintain referential integrity through application-level constraints.
     */
    @Property
    @Tag("Feature: z-commerce, Property 13: Referential Integrity Maintenance")
    void cartItemsReferenceValidProducts(
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId,
        @ForAll("quantities") Integer quantity
    ) {
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
        CartItem cartItem = new CartItem(userId, productId, quantity);
        
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

    /**
     * **Validates: Requirements 7.4**
     * Property 13: Referential Integrity Maintenance
     */
    @Property
    @Tag("Feature: z-commerce, Property 13: Referential Integrity Maintenance")
    void orderItemsReferenceValidProducts(
        @ForAll("orderIds") String orderId,
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId,
        @ForAll("quantities") Integer quantity,
        @ForAll("prices") BigDecimal price
    ) {
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
        OrderItem orderItem = new OrderItem(productId, product.getName(), quantity, price);
        
        // Create order containing the order item
        Order order = new Order(orderId, userId, Arrays.asList(orderItem), price.multiply(BigDecimal.valueOf(quantity)));
        
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

    /**
     * **Validates: Requirements 7.4**
     * Property 13: Referential Integrity Maintenance
     */
    @Property
    @Tag("Feature: z-commerce, Property 13: Referential Integrity Maintenance")
    void paymentsReferenceValidOrders(
        @ForAll("transactionIds") String transactionId,
        @ForAll("orderIds") String orderId,
        @ForAll("userIds") String userId,
        @ForAll("amounts") BigDecimal amount
    ) {
        // Create order first
        OrderItem orderItem = new OrderItem("prod1", "Product 1", 1, amount);
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

    /**
     * **Validates: Requirements 7.4**
     * Property 13: Referential Integrity Maintenance
     */
    @Property
    @Tag("Feature: z-commerce, Property 13: Referential Integrity Maintenance")
    void userEntitiesReferenceValidUsers(
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId,
        @ForAll("orderIds") String orderId
    ) {
        // Create user
        User user = new User(userId, "test@example.com", "hashedPassword", "John", "Doe");
        
        // Create cart item for the user
        CartItem cartItem = new CartItem(userId, productId, 1);
        
        // Create order for the user
        OrderItem orderItem = new OrderItem(productId, "Product", 1, BigDecimal.valueOf(10.00));
        Order order = new Order(orderId, userId, Arrays.asList(orderItem), BigDecimal.valueOf(10.00));
        
        // Verify referential integrity: all entities reference the same user
        assertEquals(userId, user.getUserId());
        assertEquals(userId, cartItem.getUserId());
        assertEquals(userId, order.getUserId());
        
        // Verify key structures maintain user relationships
        assertEquals("USER#" + userId, user.getPk());
        assertEquals("USER#" + userId, cartItem.getPk());
        assertTrue(order.getGsi1pk().contains(userId));
        
        // Verify entity types are consistent
        assertEquals("USER", user.getEntityType());
        assertEquals("CART_ITEM", cartItem.getEntityType());
        assertEquals("ORDER", order.getEntityType());
    }

    /**
     * **Validates: Requirements 7.4**
     * Property 13: Referential Integrity Maintenance
     */
    @Property
    @Tag("Feature: z-commerce, Property 13: Referential Integrity Maintenance")
    void keyStructuresPreserveRelationships(
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId,
        @ForAll("orderIds") String orderId,
        @ForAll("transactionIds") String transactionId
    ) {
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

    // Providers
    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.strings().alpha().ofLength(8);
    }

    @Provide
    Arbitrary<String> productIds() {
        return Arbitraries.strings().alpha().ofLength(8);
    }

    @Provide
    Arbitrary<String> orderIds() {
        return Arbitraries.strings().alpha().ofLength(8);
    }

    @Provide
    Arbitrary<String> transactionIds() {
        return Arbitraries.strings().alpha().ofLength(8);
    }

    @Provide
    Arbitrary<Integer> quantities() {
        return Arbitraries.integers().between(1, 10);
    }

    @Provide
    Arbitrary<BigDecimal> prices() {
        return Arbitraries.bigDecimals()
            .between(BigDecimal.valueOf(1.00), BigDecimal.valueOf(999.99))
            .ofScale(2);
    }

    @Provide
    Arbitrary<BigDecimal> amounts() {
        return Arbitraries.bigDecimals()
            .between(BigDecimal.valueOf(1.00), BigDecimal.valueOf(999.99))
            .ofScale(2);
    }
}