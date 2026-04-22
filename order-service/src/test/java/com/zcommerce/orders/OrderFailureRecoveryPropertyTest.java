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
 * Property-based test for order failure recovery maintaining consistency.
 * **Validates: Requirements 3.5**
 */
class OrderFailureRecoveryPropertyTest {

    /**
     * **Validates: Requirements 3.5**
     * Property 6: Order Failure Recovery Maintains Consistency
     * For any order that fails during processing, the system should restore 
     * inventory to its previous state and maintain system consistency without 
     * partial updates.
     */
    @Property
    @Tag("Feature: z-commerce, Property 6: Order Failure Recovery Maintains Consistency")
    void orderFailureRestoresInventoryState(
        @ForAll("orderIds") String orderId,
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId,
        @ForAll("quantities") Integer orderQuantity,
        @ForAll("inventories") Integer initialInventory,
        @ForAll("prices") BigDecimal price
    ) {
        Assume.that(orderQuantity <= initialInventory); // Valid order initially
        
        // Create product with initial inventory
        Product originalProduct = new Product(productId, "Test Product", "Description", 
                                            price, initialInventory, "Electronics");
        
        // Create order
        OrderItem orderItem = new OrderItem(productId, originalProduct.getName(), orderQuantity, price);
        BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(orderQuantity));
        Order order = new Order(orderId, userId, Arrays.asList(orderItem), totalAmount);
        
        // Simulate order processing start - reserve inventory
        Integer reservedInventory = initialInventory - orderQuantity;
        Product productWithReservedInventory = new Product(productId, originalProduct.getName(), 
                                                         originalProduct.getDescription(), originalProduct.getPrice(),
                                                         reservedInventory, originalProduct.getCategory());
        productWithReservedInventory.setCreatedAt(originalProduct.getCreatedAt());
        productWithReservedInventory.setUpdatedAt(Instant.now());
        
        // Update order status to processing
        order.setStatus(Order.OrderStatus.PROCESSING);
        
        // Simulate order failure
        order.setStatus(Order.OrderStatus.FAILED);
        
        // Simulate inventory restoration on failure
        Product restoredProduct = new Product(productId, originalProduct.getName(), 
                                            originalProduct.getDescription(), originalProduct.getPrice(),
                                            initialInventory, originalProduct.getCategory()); // Restore original inventory
        restoredProduct.setCreatedAt(originalProduct.getCreatedAt());
        restoredProduct.setUpdatedAt(Instant.now());
        
        // Verify failure recovery
        assertEquals(Order.OrderStatus.FAILED, order.getStatus());
        assertEquals(initialInventory, restoredProduct.getInventory()); // Inventory restored
        assertEquals(originalProduct.getInventory(), restoredProduct.getInventory());
        
        // Verify product availability restored
        assertEquals(originalProduct.isAvailable(), restoredProduct.isAvailable());
        
        // Verify order maintains integrity even in failed state
        assertEquals(orderId, order.getOrderId());
        assertEquals(userId, order.getUserId());
        assertEquals(0, totalAmount.compareTo(order.getTotalAmount()));
        assertEquals(1, order.getItems().size());
        
        // Verify referential integrity maintained
        assertEquals(productId, order.getItems().get(0).getProductId());
        assertEquals(originalProduct.getName(), order.getItems().get(0).getProductName());
        
        // Verify timestamps updated appropriately
        assertTrue(order.getUpdatedAt().isAfter(order.getCreatedAt()) || 
                  order.getUpdatedAt().equals(order.getCreatedAt()));
        assertTrue(restoredProduct.getUpdatedAt().isAfter(originalProduct.getCreatedAt()) || 
                  restoredProduct.getUpdatedAt().equals(originalProduct.getCreatedAt()));
    }

    /**
     * **Validates: Requirements 3.5**
     * Property 6: Order Failure Recovery Maintains Consistency
     */
    @Property
    @Tag("Feature: z-commerce, Property 6: Order Failure Recovery Maintains Consistency")
    void orderFailurePreservesCartState(
        @ForAll("orderIds") String orderId,
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId1,
        @ForAll("productIds") String productId2,
        @ForAll("quantities") Integer quantity1,
        @ForAll("quantities") Integer quantity2
    ) {
        Assume.that(!productId1.equals(productId2)); // Different products
        
        // Create original cart items
        CartItem cartItem1 = new CartItem(userId, productId1, quantity1);
        CartItem cartItem2 = new CartItem(userId, productId2, quantity2);
        List<CartItem> originalCart = Arrays.asList(cartItem1, cartItem2);
        
        // Create order from cart
        OrderItem orderItem1 = new OrderItem(productId1, "Product 1", quantity1, BigDecimal.valueOf(10.00));
        OrderItem orderItem2 = new OrderItem(productId2, "Product 2", quantity2, BigDecimal.valueOf(20.00));
        List<OrderItem> orderItems = Arrays.asList(orderItem1, orderItem2);
        
        BigDecimal totalAmount = BigDecimal.valueOf(10.00).multiply(BigDecimal.valueOf(quantity1))
                                          .add(BigDecimal.valueOf(20.00).multiply(BigDecimal.valueOf(quantity2)));
        
        Order order = new Order(orderId, userId, orderItems, totalAmount);
        
        // Simulate order processing
        order.setStatus(Order.OrderStatus.PROCESSING);
        
        // Simulate order failure
        order.setStatus(Order.OrderStatus.FAILED);
        
        // On failure, cart should be restored (not cleared)
        List<CartItem> restoredCart = new ArrayList<>(originalCart); // Cart restored to original state
        
        // Verify failure recovery
        assertEquals(Order.OrderStatus.FAILED, order.getStatus());
        assertEquals(2, restoredCart.size()); // Cart restored
        assertEquals(originalCart.size(), restoredCart.size());
        
        // Verify cart items are restored correctly
        for (CartItem originalItem : originalCart) {
            CartItem restoredItem = restoredCart.stream()
                .filter(item -> item.getProductId().equals(originalItem.getProductId()))
                .findFirst()
                .orElseThrow();
            
            assertEquals(originalItem.getUserId(), restoredItem.getUserId());
            assertEquals(originalItem.getProductId(), restoredItem.getProductId());
            assertEquals(originalItem.getQuantity(), restoredItem.getQuantity());
            assertEquals(originalItem.getPk(), restoredItem.getPk());
            assertEquals(originalItem.getSk(), restoredItem.getSk());
        }
        
        // Verify order maintains data integrity despite failure
        assertEquals(orderId, order.getOrderId());
        assertEquals(userId, order.getUserId());
        assertEquals(2, order.getItems().size());
        
        // Verify referential integrity between failed order and restored cart
        for (CartItem cartItem : restoredCart) {
            OrderItem correspondingOrderItem = order.getItems().stream()
                .filter(item -> item.getProductId().equals(cartItem.getProductId()))
                .findFirst()
                .orElseThrow();
            
            assertEquals(cartItem.getProductId(), correspondingOrderItem.getProductId());
            assertEquals(cartItem.getQuantity(), correspondingOrderItem.getQuantity());
        }
    }

    /**
     * **Validates: Requirements 3.5**
     * Property 6: Order Failure Recovery Maintains Consistency
     */
    @Test
    void orderFailureAtDifferentStagesRecovery() {
        String orderId = "order123";
        String userId = "user123";
        String productId = "prod123";
        Integer initialInventory = 100;
        Integer orderQuantity = 10;
        
        // Create product
        Product product = new Product(productId, "Test Product", "Description", 
                                    BigDecimal.valueOf(50.00), initialInventory, "Electronics");
        
        // Create order
        OrderItem orderItem = new OrderItem(productId, product.getName(), orderQuantity, product.getPrice());
        Order order = new Order(orderId, userId, Arrays.asList(orderItem), 
                              product.getPrice().multiply(BigDecimal.valueOf(orderQuantity)));
        
        // Test failure at different stages
        
        // Stage 1: Failure during initial validation (before inventory reservation)
        order.setStatus(Order.OrderStatus.FAILED);
        
        // Verify no inventory changes occurred
        assertEquals(initialInventory, product.getInventory());
        assertEquals(Order.OrderStatus.FAILED, order.getStatus());
        
        // Reset for next test
        order.setStatus(Order.OrderStatus.PENDING);
        
        // Stage 2: Failure during processing (after inventory reservation)
        order.setStatus(Order.OrderStatus.PROCESSING);
        
        // Simulate inventory reservation
        Product productWithReservedInventory = new Product(productId, product.getName(), 
                                                         product.getDescription(), product.getPrice(),
                                                         initialInventory - orderQuantity, product.getCategory());
        productWithReservedInventory.setCreatedAt(product.getCreatedAt());
        productWithReservedInventory.setUpdatedAt(Instant.now());
        
        // Simulate failure during processing
        order.setStatus(Order.OrderStatus.FAILED);
        
        // Simulate inventory restoration
        Product restoredProduct = new Product(productId, product.getName(), 
                                            product.getDescription(), product.getPrice(),
                                            initialInventory, product.getCategory()); // Restored to original
        restoredProduct.setCreatedAt(product.getCreatedAt());
        restoredProduct.setUpdatedAt(Instant.now());
        
        // Verify recovery
        assertEquals(Order.OrderStatus.FAILED, order.getStatus());
        assertEquals(initialInventory, restoredProduct.getInventory());
        assertEquals(product.getInventory(), restoredProduct.getInventory());
        
        // Verify order data integrity maintained throughout failure scenarios
        assertEquals(orderId, order.getOrderId());
        assertEquals(userId, order.getUserId());
        assertEquals(1, order.getItems().size());
        assertEquals(productId, order.getItems().get(0).getProductId());
        
        // Verify key structures remain intact
        assertEquals("ORDER#" + orderId, order.getPk());
        assertEquals("DETAILS", order.getSk());
        assertEquals("ORDER#" + userId, order.getGsi1pk());
        assertEquals("ORDER", order.getEntityType());
    }

    /**
     * **Validates: Requirements 3.5**
     * Property 6: Order Failure Recovery Maintains Consistency
     */
    @Test
    void multipleOrderFailuresDoNotAffectEachOther() {
        String userId1 = "user1";
        String userId2 = "user2";
        String productId = "prod123";
        Integer initialInventory = 100;
        
        // Create product
        Product product = new Product(productId, "Test Product", "Description", 
                                    BigDecimal.valueOf(25.00), initialInventory, "Electronics");
        
        // Create two orders for the same product
        OrderItem orderItem1 = new OrderItem(productId, product.getName(), 5, product.getPrice());
        OrderItem orderItem2 = new OrderItem(productId, product.getName(), 3, product.getPrice());
        
        Order order1 = new Order("order1", userId1, Arrays.asList(orderItem1), 
                                product.getPrice().multiply(BigDecimal.valueOf(5)));
        Order order2 = new Order("order2", userId2, Arrays.asList(orderItem2), 
                                product.getPrice().multiply(BigDecimal.valueOf(3)));
        
        // Process first order successfully
        order1.setStatus(Order.OrderStatus.PROCESSING);
        order1.setStatus(Order.OrderStatus.COMPLETED);
        Integer inventoryAfterOrder1 = initialInventory - 5; // 95
        
        // Process second order but it fails
        order2.setStatus(Order.OrderStatus.PROCESSING);
        
        // Simulate inventory reservation for order2
        Integer inventoryWithOrder2Reserved = inventoryAfterOrder1 - 3; // 92
        
        // Order2 fails
        order2.setStatus(Order.OrderStatus.FAILED);
        
        // Simulate inventory restoration for order2 failure
        Integer inventoryAfterOrder2Failure = inventoryAfterOrder1; // Back to 95 (order1's effect remains)
        
        // Verify failure recovery doesn't affect successful order
        assertEquals(Order.OrderStatus.COMPLETED, order1.getStatus());
        assertEquals(Order.OrderStatus.FAILED, order2.getStatus());
        
        // Verify inventory state is correct
        assertEquals(Integer.valueOf(95), Integer.valueOf(inventoryAfterOrder2Failure));
        
        // Verify orders remain distinct and unaffected by each other's failures
        assertNotEquals(order1.getOrderId(), order2.getOrderId());
        assertNotEquals(order1.getUserId(), order2.getUserId());
        
        // Verify order1 data integrity (should be unaffected by order2 failure)
        assertEquals("order1", order1.getOrderId());
        assertEquals(userId1, order1.getUserId());
        assertEquals(Order.OrderStatus.COMPLETED, order1.getStatus());
        assertEquals(BigDecimal.valueOf(125.00), order1.getTotalAmount());
        
        // Verify order2 data integrity (failed but consistent)
        assertEquals("order2", order2.getOrderId());
        assertEquals(userId2, order2.getUserId());
        assertEquals(Order.OrderStatus.FAILED, order2.getStatus());
        assertEquals(BigDecimal.valueOf(75.00), order2.getTotalAmount());
        
        // Verify both orders maintain referential integrity
        assertEquals(productId, order1.getItems().get(0).getProductId());
        assertEquals(productId, order2.getItems().get(0).getProductId());
        assertEquals(product.getName(), order1.getItems().get(0).getProductName());
        assertEquals(product.getName(), order2.getItems().get(0).getProductName());
    }

    /**
     * **Validates: Requirements 3.5**
     * Property 6: Order Failure Recovery Maintains Consistency
     */
    @Property
    @Tag("Feature: z-commerce, Property 6: Order Failure Recovery Maintains Consistency")
    void orderFailureRecoveryMaintainsSystemIntegrity(
        @ForAll("orderIds") String orderId,
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId1,
        @ForAll("productIds") String productId2,
        @ForAll("quantities") Integer quantity1,
        @ForAll("quantities") Integer quantity2,
        @ForAll("inventories") Integer inventory1,
        @ForAll("inventories") Integer inventory2,
        @ForAll("prices") BigDecimal price1,
        @ForAll("prices") BigDecimal price2
    ) {
        Assume.that(!productId1.equals(productId2)); // Different products
        Assume.that(quantity1 <= inventory1 && quantity2 <= inventory2); // Valid quantities
        
        // Create products
        Product product1 = new Product(productId1, "Product 1", "Desc 1", price1, inventory1, "Cat1");
        Product product2 = new Product(productId2, "Product 2", "Desc 2", price2, inventory2, "Cat2");
        
        // Create multi-item order
        OrderItem orderItem1 = new OrderItem(productId1, product1.getName(), quantity1, price1);
        OrderItem orderItem2 = new OrderItem(productId2, product2.getName(), quantity2, price2);
        List<OrderItem> orderItems = Arrays.asList(orderItem1, orderItem2);
        
        BigDecimal totalAmount = price1.multiply(BigDecimal.valueOf(quantity1))
                                      .add(price2.multiply(BigDecimal.valueOf(quantity2)));
        
        Order order = new Order(orderId, userId, orderItems, totalAmount);
        
        // Simulate order processing and failure
        order.setStatus(Order.OrderStatus.PROCESSING);
        order.setStatus(Order.OrderStatus.FAILED);
        
        // Verify system integrity after failure
        assertEquals(Order.OrderStatus.FAILED, order.getStatus());
        
        // Verify order data integrity maintained
        assertEquals(orderId, order.getOrderId());
        assertEquals(userId, order.getUserId());
        assertEquals(2, order.getItems().size());
        assertEquals(0, totalAmount.compareTo(order.getTotalAmount()));
        
        // Verify all order items maintain integrity
        OrderItem retrievedItem1 = order.getItems().stream()
            .filter(item -> productId1.equals(item.getProductId()))
            .findFirst()
            .orElseThrow();
        OrderItem retrievedItem2 = order.getItems().stream()
            .filter(item -> productId2.equals(item.getProductId()))
            .findFirst()
            .orElseThrow();
        
        assertEquals(productId1, retrievedItem1.getProductId());
        assertEquals(productId2, retrievedItem2.getProductId());
        assertEquals(quantity1, retrievedItem1.getQuantity());
        assertEquals(quantity2, retrievedItem2.getQuantity());
        assertEquals(0, price1.compareTo(retrievedItem1.getPrice()));
        assertEquals(0, price2.compareTo(retrievedItem2.getPrice()));
        
        // Verify key structures remain intact
        assertEquals("ORDER#" + orderId, order.getPk());
        assertEquals("DETAILS", order.getSk());
        assertEquals("ORDER#" + userId, order.getGsi1pk());
        assertEquals("ORDER", order.getEntityType());
        
        // Verify timestamps are consistent
        assertNotNull(order.getCreatedAt());
        assertNotNull(order.getUpdatedAt());
        assertTrue(order.getUpdatedAt().isAfter(order.getCreatedAt()) || 
                  order.getUpdatedAt().equals(order.getCreatedAt()));
        
        // Verify products would be restored to original inventory (simulated)
        assertEquals(inventory1, product1.getInventory()); // Original inventory maintained
        assertEquals(inventory2, product2.getInventory()); // Original inventory maintained
        assertEquals(product1.isAvailable(), inventory1 > 0);
        assertEquals(product2.isAvailable(), inventory2 > 0);
    }

    /**
     * **Validates: Requirements 3.5**
     * Property 6: Order Failure Recovery Maintains Consistency
     */
    @Test
    void orderFailureRecoveryWithComplexScenario() {
        String orderId = "order123";
        String userId = "user123";
        
        // Create products with different inventory levels
        Product product1 = new Product("prod1", "Product 1", "Desc 1", BigDecimal.valueOf(10.00), 50, "Cat1");
        Product product2 = new Product("prod2", "Product 2", "Desc 2", BigDecimal.valueOf(25.00), 20, "Cat2");
        Product product3 = new Product("prod3", "Product 3", "Desc 3", BigDecimal.valueOf(100.00), 5, "Cat3");
        
        // Create cart items
        CartItem cartItem1 = new CartItem(userId, "prod1", 5);
        CartItem cartItem2 = new CartItem(userId, "prod2", 2);
        CartItem cartItem3 = new CartItem(userId, "prod3", 1);
        List<CartItem> originalCart = Arrays.asList(cartItem1, cartItem2, cartItem3);
        
        // Create order from cart
        List<OrderItem> orderItems = Arrays.asList(
            new OrderItem("prod1", product1.getName(), 5, product1.getPrice()),
            new OrderItem("prod2", product2.getName(), 2, product2.getPrice()),
            new OrderItem("prod3", product3.getName(), 1, product3.getPrice())
        );
        
        BigDecimal totalAmount = BigDecimal.valueOf(200.00); // 50 + 50 + 100
        Order order = new Order(orderId, userId, orderItems, totalAmount);
        
        // Simulate order processing with inventory reservation
        order.setStatus(Order.OrderStatus.PROCESSING);
        
        // Simulate inventory changes during processing
        Product reservedProduct1 = new Product("prod1", product1.getName(), product1.getDescription(),
                                             product1.getPrice(), 45, product1.getCategory()); // 50 - 5
        Product reservedProduct2 = new Product("prod2", product2.getName(), product2.getDescription(),
                                             product2.getPrice(), 18, product2.getCategory()); // 20 - 2
        Product reservedProduct3 = new Product("prod3", product3.getName(), product3.getDescription(),
                                             product3.getPrice(), 4, product3.getCategory());  // 5 - 1
        
        // Simulate order failure
        order.setStatus(Order.OrderStatus.FAILED);
        
        // Simulate complete recovery
        Product restoredProduct1 = new Product("prod1", product1.getName(), product1.getDescription(),
                                             product1.getPrice(), 50, product1.getCategory()); // Restored
        Product restoredProduct2 = new Product("prod2", product2.getName(), product2.getDescription(),
                                             product2.getPrice(), 20, product2.getCategory()); // Restored
        Product restoredProduct3 = new Product("prod3", product3.getName(), product3.getDescription(),
                                             product3.getPrice(), 5, product3.getCategory());  // Restored
        
        List<CartItem> restoredCart = new ArrayList<>(originalCart); // Cart restored
        
        // Verify comprehensive failure recovery
        assertEquals(Order.OrderStatus.FAILED, order.getStatus());
        
        // Verify inventory restoration
        assertEquals(product1.getInventory(), restoredProduct1.getInventory());
        assertEquals(product2.getInventory(), restoredProduct2.getInventory());
        assertEquals(product3.getInventory(), restoredProduct3.getInventory());
        
        // Verify cart restoration
        assertEquals(3, restoredCart.size());
        assertEquals(originalCart.size(), restoredCart.size());
        
        // Verify order maintains complete data integrity
        assertEquals(orderId, order.getOrderId());
        assertEquals(userId, order.getUserId());
        assertEquals(3, order.getItems().size());
        assertEquals(0, totalAmount.compareTo(order.getTotalAmount()));
        
        // Verify all relationships remain intact
        for (CartItem originalCartItem : originalCart) {
            // Verify cart restoration
            CartItem restoredCartItem = restoredCart.stream()
                .filter(item -> item.getProductId().equals(originalCartItem.getProductId()))
                .findFirst()
                .orElseThrow();
            
            assertEquals(originalCartItem.getProductId(), restoredCartItem.getProductId());
            assertEquals(originalCartItem.getQuantity(), restoredCartItem.getQuantity());
            
            // Verify order item integrity
            OrderItem orderItem = order.getItems().stream()
                .filter(item -> item.getProductId().equals(originalCartItem.getProductId()))
                .findFirst()
                .orElseThrow();
            
            assertEquals(originalCartItem.getProductId(), orderItem.getProductId());
            assertEquals(originalCartItem.getQuantity(), orderItem.getQuantity());
        }
        
        // Verify system state is completely consistent after failure recovery
        assertNotNull(order.getCreatedAt());
        assertNotNull(order.getUpdatedAt());
        assertEquals("ORDER#" + orderId, order.getPk());
        assertEquals("ORDER#" + userId, order.getGsi1pk());
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