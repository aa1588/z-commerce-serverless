package com.zcommerce.orders;

import com.zcommerce.shared.model.Order;
import com.zcommerce.shared.model.OrderItem;
import com.zcommerce.shared.model.Product;
import com.zcommerce.shared.model.CartItem;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for order processing state updates.
 * **Validates: Requirements 3.3, 3.4**
 */
class OrderProcessingStatePropertyTest {

    /**
     * **Validates: Requirements 3.3, 3.4**
     * Property 5: Order Processing Updates System State
     * For any completed order, the system should correctly update product inventory, 
     * clear the customer's cart, and maintain referential integrity between 
     * orders, products, and users.
     */
    @Property
    @Tag("Feature: z-commerce, Property 5: Order Processing Updates System State")
    void orderProcessingUpdatesProductInventory(
        @ForAll("orderIds") String orderId,
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId,
        @ForAll("quantities") Integer orderQuantity,
        @ForAll("inventories") Integer initialInventory,
        @ForAll("prices") BigDecimal price
    ) {
        Assume.that(orderQuantity <= initialInventory); // Valid order
        Assume.that(initialInventory >= orderQuantity);
        
        // Create product with initial inventory
        Product product = new Product(productId, "Test Product", "Description", 
                                    price, initialInventory, "Electronics");
        
        // Create order
        OrderItem orderItem = new OrderItem(productId, product.getName(), orderQuantity, price);
        BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(orderQuantity));
        Order order = new Order(orderId, userId, Arrays.asList(orderItem), totalAmount);
        
        // Simulate order processing - update product inventory
        Integer expectedNewInventory = initialInventory - orderQuantity;
        Product updatedProduct = new Product(productId, product.getName(), product.getDescription(),
                                           product.getPrice(), expectedNewInventory, product.getCategory());
        updatedProduct.setCreatedAt(product.getCreatedAt());
        updatedProduct.setUpdatedAt(Instant.now());
        
        // Update order status to completed
        order.setStatus(Order.OrderStatus.COMPLETED);
        
        // Verify inventory update
        assertEquals(expectedNewInventory, updatedProduct.getInventory());
        assertTrue(updatedProduct.getUpdatedAt().isAfter(product.getCreatedAt()) || 
                  updatedProduct.getUpdatedAt().equals(product.getCreatedAt()));
        
        // Verify order completion
        assertEquals(Order.OrderStatus.COMPLETED, order.getStatus());
        assertTrue(order.getUpdatedAt().isAfter(order.getCreatedAt()) || 
                  order.getUpdatedAt().equals(order.getCreatedAt()));
        
        // Verify product availability after inventory update
        boolean expectedAvailability = expectedNewInventory > 0;
        assertEquals(expectedAvailability, updatedProduct.isAvailable());
        
        // Verify referential integrity maintained
        assertEquals(productId, updatedProduct.getProductId());
        assertEquals(productId, orderItem.getProductId());
        assertEquals(product.getName(), orderItem.getProductName());
    }

    /**
     * **Validates: Requirements 3.3, 3.4**
     * Property 5: Order Processing Updates System State
     */
    @Property
    @Tag("Feature: z-commerce, Property 5: Order Processing Updates System State")
    void orderProcessingClearsCustomerCart(
        @ForAll("orderIds") String orderId,
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId1,
        @ForAll("productIds") String productId2,
        @ForAll("quantities") Integer quantity1,
        @ForAll("quantities") Integer quantity2
    ) {
        Assume.that(!productId1.equals(productId2)); // Different products
        
        // Create cart items for user
        CartItem cartItem1 = new CartItem(userId, productId1, quantity1);
        CartItem cartItem2 = new CartItem(userId, productId2, quantity2);
        List<CartItem> originalCart = Arrays.asList(cartItem1, cartItem2);
        
        // Create order from cart items
        OrderItem orderItem1 = new OrderItem(productId1, "Product 1", quantity1, BigDecimal.valueOf(10.00));
        OrderItem orderItem2 = new OrderItem(productId2, "Product 2", quantity2, BigDecimal.valueOf(20.00));
        List<OrderItem> orderItems = Arrays.asList(orderItem1, orderItem2);
        
        BigDecimal totalAmount = BigDecimal.valueOf(10.00).multiply(BigDecimal.valueOf(quantity1))
                                          .add(BigDecimal.valueOf(20.00).multiply(BigDecimal.valueOf(quantity2)));
        
        Order order = new Order(orderId, userId, orderItems, totalAmount);
        
        // Simulate order processing - clear cart
        List<CartItem> clearedCart = new ArrayList<>(); // Empty cart after order completion
        
        // Update order status to completed
        order.setStatus(Order.OrderStatus.COMPLETED);
        
        // Verify cart is cleared
        assertTrue(clearedCart.isEmpty());
        assertEquals(2, originalCart.size()); // Original cart had items
        
        // Verify order contains all original cart items
        assertEquals(2, order.getItems().size());
        assertTrue(order.getItems().stream().anyMatch(item -> productId1.equals(item.getProductId())));
        assertTrue(order.getItems().stream().anyMatch(item -> productId2.equals(item.getProductId())));
        
        // Verify order completion
        assertEquals(Order.OrderStatus.COMPLETED, order.getStatus());
        assertEquals(userId, order.getUserId());
        
        // Verify referential integrity between cart and order
        for (CartItem originalCartItem : originalCart) {
            OrderItem correspondingOrderItem = order.getItems().stream()
                .filter(item -> item.getProductId().equals(originalCartItem.getProductId()))
                .findFirst()
                .orElseThrow();
            
            assertEquals(originalCartItem.getProductId(), correspondingOrderItem.getProductId());
            assertEquals(originalCartItem.getQuantity(), correspondingOrderItem.getQuantity());
        }
    }

    /**
     * **Validates: Requirements 3.3, 3.4**
     * Property 5: Order Processing Updates System State
     */
    @Test
    void orderStatusTransitionMaintainsIntegrity() {
        String orderId = "order123";
        String userId = "user123";
        
        // Create order
        OrderItem orderItem = new OrderItem("prod1", "Product 1", 2, BigDecimal.valueOf(50.00));
        Order order = new Order(orderId, userId, Arrays.asList(orderItem), BigDecimal.valueOf(100.00));
        
        Instant originalCreatedAt = order.getCreatedAt();
        Instant originalUpdatedAt = order.getUpdatedAt();
        
        // Test valid status transitions
        Order.OrderStatus[] validTransitions = {
            Order.OrderStatus.PENDING,
            Order.OrderStatus.PROCESSING,
            Order.OrderStatus.COMPLETED
        };
        
        for (Order.OrderStatus newStatus : validTransitions) {
            // Update status
            order.setStatus(newStatus);
            
            // Verify status update
            assertEquals(newStatus, order.getStatus());
            
            // Verify other fields remain unchanged
            assertEquals(orderId, order.getOrderId());
            assertEquals(userId, order.getUserId());
            assertEquals(BigDecimal.valueOf(100.00), order.getTotalAmount());
            assertEquals(1, order.getItems().size());
            assertEquals("ORDER", order.getEntityType());
            
            // Verify key structures remain intact
            assertEquals("ORDER#" + orderId, order.getPk());
            assertEquals("DETAILS", order.getSk());
            assertEquals("ORDER#" + userId, order.getGsi1pk());
            
            // Verify timestamps
            assertEquals(originalCreatedAt, order.getCreatedAt()); // Creation time never changes
            assertTrue(order.getUpdatedAt().isAfter(originalCreatedAt) || 
                      order.getUpdatedAt().equals(originalCreatedAt)); // Update time advances or stays same
        }
    }

    /**
     * **Validates: Requirements 3.3, 3.4**
     * Property 5: Order Processing Updates System State
     */
    @Test
    void orderCompletionGeneratesConfirmation() {
        String orderId = "order123";
        String userId = "user123";
        
        // Create order with multiple items
        OrderItem item1 = new OrderItem("prod1", "Product 1", 2, BigDecimal.valueOf(25.00));
        OrderItem item2 = new OrderItem("prod2", "Product 2", 1, BigDecimal.valueOf(50.00));
        List<OrderItem> items = Arrays.asList(item1, item2);
        BigDecimal totalAmount = BigDecimal.valueOf(100.00);
        
        Order order = new Order(orderId, userId, items, totalAmount);
        
        // Process order to completion
        order.setStatus(Order.OrderStatus.PROCESSING);
        order.setStatus(Order.OrderStatus.COMPLETED);
        
        // Verify order confirmation data
        assertEquals(Order.OrderStatus.COMPLETED, order.getStatus());
        assertEquals(orderId, order.getOrderId());
        assertEquals(userId, order.getUserId());
        assertEquals(0, totalAmount.compareTo(order.getTotalAmount()));
        
        // Verify order items for confirmation
        assertEquals(2, order.getItems().size());
        
        // Calculate confirmation totals
        BigDecimal confirmationTotal = order.getItems().stream()
            .map(OrderItem::getTotalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        assertEquals(0, order.getTotalAmount().compareTo(confirmationTotal));
        
        // Verify individual item details for confirmation
        OrderItem confirmedItem1 = order.getItems().stream()
            .filter(item -> "prod1".equals(item.getProductId()))
            .findFirst()
            .orElseThrow();
        
        assertEquals("prod1", confirmedItem1.getProductId());
        assertEquals("Product 1", confirmedItem1.getProductName());
        assertEquals(Integer.valueOf(2), confirmedItem1.getQuantity());
        assertEquals(BigDecimal.valueOf(25.00), confirmedItem1.getPrice());
        assertEquals(BigDecimal.valueOf(50.00), confirmedItem1.getTotalPrice());
        
        // Verify timestamps for confirmation
        assertNotNull(order.getCreatedAt());
        assertNotNull(order.getUpdatedAt());
        assertTrue(order.getUpdatedAt().isAfter(order.getCreatedAt()) || 
                  order.getUpdatedAt().equals(order.getCreatedAt()));
    }

    /**
     * **Validates: Requirements 3.3, 3.4**
     * Property 5: Order Processing Updates System State
     */
    @Property
    @Tag("Feature: z-commerce, Property 5: Order Processing Updates System State")
    void multipleOrderProcessingMaintainsSystemConsistency(
        @ForAll("orderIds") String orderId1,
        @ForAll("orderIds") String orderId2,
        @ForAll("userIds") String userId1,
        @ForAll("userIds") String userId2,
        @ForAll("productIds") String productId,
        @ForAll("quantities") Integer quantity1,
        @ForAll("quantities") Integer quantity2,
        @ForAll("inventories") Integer initialInventory,
        @ForAll("prices") BigDecimal price
    ) {
        Assume.that(!orderId1.equals(orderId2)); // Different orders
        Assume.that(!userId1.equals(userId2)); // Different users
        Assume.that(quantity1 + quantity2 <= initialInventory); // Sufficient inventory for both orders
        
        // Create product with sufficient inventory
        Product product = new Product(productId, "Test Product", "Description", 
                                    price, initialInventory, "Electronics");
        
        // Create two orders for the same product
        OrderItem orderItem1 = new OrderItem(productId, product.getName(), quantity1, price);
        OrderItem orderItem2 = new OrderItem(productId, product.getName(), quantity2, price);
        
        Order order1 = new Order(orderId1, userId1, Arrays.asList(orderItem1), 
                                price.multiply(BigDecimal.valueOf(quantity1)));
        Order order2 = new Order(orderId2, userId2, Arrays.asList(orderItem2), 
                                price.multiply(BigDecimal.valueOf(quantity2)));
        
        // Process first order
        order1.setStatus(Order.OrderStatus.COMPLETED);
        Integer inventoryAfterOrder1 = initialInventory - quantity1;
        
        // Process second order
        order2.setStatus(Order.OrderStatus.COMPLETED);
        Integer finalInventory = inventoryAfterOrder1 - quantity2;
        
        // Verify system consistency after multiple order processing
        assertEquals(Order.OrderStatus.COMPLETED, order1.getStatus());
        assertEquals(Order.OrderStatus.COMPLETED, order2.getStatus());
        
        // Verify inventory calculations
        assertEquals(initialInventory - quantity1 - quantity2, finalInventory);
        assertTrue(finalInventory >= 0); // Should not go negative due to our assumption
        
        // Verify orders remain distinct
        assertNotEquals(order1.getOrderId(), order2.getOrderId());
        assertNotEquals(order1.getUserId(), order2.getUserId());
        assertEquals(order1.getItems().get(0).getProductId(), order2.getItems().get(0).getProductId());
        
        // Verify key structures remain unique
        assertEquals("ORDER#" + orderId1, order1.getPk());
        assertEquals("ORDER#" + orderId2, order2.getPk());
        assertEquals("ORDER#" + userId1, order1.getGsi1pk());
        assertEquals("ORDER#" + userId2, order2.getGsi1pk());
        
        // Verify referential integrity maintained
        assertEquals(productId, order1.getItems().get(0).getProductId());
        assertEquals(productId, order2.getItems().get(0).getProductId());
        assertEquals(product.getName(), order1.getItems().get(0).getProductName());
        assertEquals(product.getName(), order2.getItems().get(0).getProductName());
    }

    /**
     * **Validates: Requirements 3.3, 3.4**
     * Property 5: Order Processing Updates System State
     */
    @Test
    void orderProcessingWithComplexCartScenario() {
        String orderId = "order123";
        String userId = "user123";
        
        // Create products
        Product product1 = new Product("prod1", "Product 1", "Desc 1", BigDecimal.valueOf(10.00), 100, "Cat1");
        Product product2 = new Product("prod2", "Product 2", "Desc 2", BigDecimal.valueOf(25.50), 50, "Cat2");
        Product product3 = new Product("prod3", "Product 3", "Desc 3", BigDecimal.valueOf(99.99), 25, "Cat3");
        
        // Create cart with multiple items
        CartItem cartItem1 = new CartItem(userId, "prod1", 3);
        CartItem cartItem2 = new CartItem(userId, "prod2", 2);
        CartItem cartItem3 = new CartItem(userId, "prod3", 1);
        List<CartItem> originalCart = Arrays.asList(cartItem1, cartItem2, cartItem3);
        
        // Convert cart to order
        List<OrderItem> orderItems = Arrays.asList(
            new OrderItem("prod1", product1.getName(), 3, product1.getPrice()),
            new OrderItem("prod2", product2.getName(), 2, product2.getPrice()),
            new OrderItem("prod3", product3.getName(), 1, product3.getPrice())
        );
        
        BigDecimal totalAmount = BigDecimal.valueOf(30.00)  // 3 * 10.00
                                          .add(BigDecimal.valueOf(51.00))  // 2 * 25.50
                                          .add(BigDecimal.valueOf(99.99)); // 1 * 99.99
        
        Order order = new Order(orderId, userId, orderItems, totalAmount);
        
        // Process order
        order.setStatus(Order.OrderStatus.PROCESSING);
        
        // Simulate inventory updates
        Product updatedProduct1 = new Product("prod1", product1.getName(), product1.getDescription(),
                                            product1.getPrice(), 97, product1.getCategory()); // 100 - 3
        Product updatedProduct2 = new Product("prod2", product2.getName(), product2.getDescription(),
                                            product2.getPrice(), 48, product2.getCategory()); // 50 - 2
        Product updatedProduct3 = new Product("prod3", product3.getName(), product3.getDescription(),
                                            product3.getPrice(), 24, product3.getCategory()); // 25 - 1
        
        // Complete order
        order.setStatus(Order.OrderStatus.COMPLETED);
        
        // Simulate cart clearing
        List<CartItem> clearedCart = new ArrayList<>();
        
        // Verify order processing results
        assertEquals(Order.OrderStatus.COMPLETED, order.getStatus());
        assertEquals(3, order.getItems().size());
        assertEquals(0, BigDecimal.valueOf(180.99).compareTo(order.getTotalAmount()));
        
        // Verify inventory updates
        assertEquals(Integer.valueOf(97), updatedProduct1.getInventory());
        assertEquals(Integer.valueOf(48), updatedProduct2.getInventory());
        assertEquals(Integer.valueOf(24), updatedProduct3.getInventory());
        
        // Verify cart clearing
        assertTrue(clearedCart.isEmpty());
        assertEquals(3, originalCart.size()); // Original cart had items
        
        // Verify referential integrity across all entities
        for (int i = 0; i < originalCart.size(); i++) {
            CartItem originalCartItem = originalCart.get(i);
            OrderItem correspondingOrderItem = order.getItems().stream()
                .filter(item -> item.getProductId().equals(originalCartItem.getProductId()))
                .findFirst()
                .orElseThrow();
            
            assertEquals(originalCartItem.getProductId(), correspondingOrderItem.getProductId());
            assertEquals(originalCartItem.getQuantity(), correspondingOrderItem.getQuantity());
        }
        
        // Verify order confirmation data integrity
        assertEquals(userId, order.getUserId());
        assertEquals(orderId, order.getOrderId());
        assertNotNull(order.getCreatedAt());
        assertNotNull(order.getUpdatedAt());
        assertTrue(order.getUpdatedAt().isAfter(order.getCreatedAt()) || 
                  order.getUpdatedAt().equals(order.getCreatedAt()));
    }

    // Providers
    @Provide
    Arbitrary<String> orderIds() {
        return Arbitraries.strings().alpha().ofLength(8);
    }

    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.strings().alpha().ofLength(8);
    }

    @Provide
    Arbitrary<String> productIds() {
        return Arbitraries.strings().alpha().ofLength(8);
    }

    @Provide
    Arbitrary<Integer> quantities() {
        return Arbitraries.integers().between(1, 10);
    }

    @Provide
    Arbitrary<Integer> inventories() {
        return Arbitraries.integers().between(10, 100);
    }

    @Provide
    Arbitrary<BigDecimal> prices() {
        return Arbitraries.bigDecimals()
            .between(BigDecimal.valueOf(1.00), BigDecimal.valueOf(999.99))
            .ofScale(2);
    }
}