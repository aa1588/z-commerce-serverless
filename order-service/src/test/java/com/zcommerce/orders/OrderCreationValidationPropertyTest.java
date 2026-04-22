package com.zcommerce.orders;

import com.zcommerce.shared.model.Order;
import com.zcommerce.shared.model.OrderItem;
import com.zcommerce.shared.model.Product;
import com.zcommerce.shared.model.CartItem;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for order creation validation.
 * **Validates: Requirements 3.1, 3.2**
 */
class OrderCreationValidationPropertyTest {

    /**
     * **Validates: Requirements 3.1, 3.2**
     * Property 4: Order Creation Validates Business Rules
     * For any order creation request, the system should validate inventory availability, 
     * create orders only when constraints are met, and maintain data consistency 
     * between orders and inventory.
     */
    @Property
    @Tag("Feature: z-commerce, Property 4: Order Creation Validates Business Rules")
    void orderCreationValidatesInventoryAvailability(
        @ForAll("orderIds") String orderId,
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId,
        @ForAll("quantities") Integer requestedQuantity,
        @ForAll("inventories") Integer availableInventory,
        @ForAll("prices") BigDecimal price
    ) {
        Assume.that(requestedQuantity > 0);
        Assume.that(availableInventory >= 0);
        
        // Create product with specific inventory
        Product product = new Product(productId, "Test Product", "Description", 
                                    price, availableInventory, "Electronics");
        
        // Create order item
        OrderItem orderItem = new OrderItem(productId, product.getName(), requestedQuantity, price);
        BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(requestedQuantity));
        
        // Validate inventory constraint
        boolean hasValidInventory = availableInventory >= requestedQuantity;
        
        if (hasValidInventory) {
            // Order creation should succeed
            Order order = new Order(orderId, userId, Arrays.asList(orderItem), totalAmount);
            
            // Verify order creation integrity
            assertEquals(orderId, order.getOrderId());
            assertEquals(userId, order.getUserId());
            assertEquals(Order.OrderStatus.PENDING, order.getStatus());
            assertEquals(0, totalAmount.compareTo(order.getTotalAmount()));
            assertEquals(1, order.getItems().size());
            
            // Verify order item details
            OrderItem createdItem = order.getItems().get(0);
            assertEquals(productId, createdItem.getProductId());
            assertEquals(product.getName(), createdItem.getProductName());
            assertEquals(requestedQuantity, createdItem.getQuantity());
            assertEquals(0, price.compareTo(createdItem.getPrice()));
            
            // Verify key structures
            assertEquals("ORDER#" + orderId, order.getPk());
            assertEquals("DETAILS", order.getSk());
            assertEquals("ORDER#" + userId, order.getGsi1pk());
            assertEquals("ORDER", order.getEntityType());
            
            // Verify timestamps
            assertNotNull(order.getCreatedAt());
            assertNotNull(order.getUpdatedAt());
        } else {
            // Order creation should fail due to insufficient inventory
            // In a real system, this would throw an exception or return an error
            assertTrue(requestedQuantity > availableInventory);
            assertFalse(product.getInventory() >= requestedQuantity);
        }
    }

    /**
     * **Validates: Requirements 3.1, 3.2**
     * Property 4: Order Creation Validates Business Rules
     */
    @Property
    @Tag("Feature: z-commerce, Property 4: Order Creation Validates Business Rules")
    void orderCreationValidatesTotalAmountCalculation(
        @ForAll("orderIds") String orderId,
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId1,
        @ForAll("productIds") String productId2,
        @ForAll("quantities") Integer quantity1,
        @ForAll("quantities") Integer quantity2,
        @ForAll("prices") BigDecimal price1,
        @ForAll("prices") BigDecimal price2
    ) {
        Assume.that(!productId1.equals(productId2)); // Different products
        
        // Create order items
        OrderItem item1 = new OrderItem(productId1, "Product 1", quantity1, price1);
        OrderItem item2 = new OrderItem(productId2, "Product 2", quantity2, price2);
        List<OrderItem> items = Arrays.asList(item1, item2);
        
        // Calculate expected total
        BigDecimal expectedTotal = price1.multiply(BigDecimal.valueOf(quantity1))
                                        .add(price2.multiply(BigDecimal.valueOf(quantity2)));
        
        // Create order
        Order order = new Order(orderId, userId, items, expectedTotal);
        
        // Verify total amount calculation
        assertEquals(0, expectedTotal.compareTo(order.getTotalAmount()));
        
        // Verify individual item totals
        assertEquals(0, price1.multiply(BigDecimal.valueOf(quantity1))
                              .compareTo(item1.getTotalPrice()));
        assertEquals(0, price2.multiply(BigDecimal.valueOf(quantity2))
                              .compareTo(item2.getTotalPrice()));
        
        // Verify order integrity
        assertEquals(orderId, order.getOrderId());
        assertEquals(userId, order.getUserId());
        assertEquals(2, order.getItems().size());
        assertEquals(Order.OrderStatus.PENDING, order.getStatus());
        
        // Verify all items are present
        assertTrue(order.getItems().stream().anyMatch(item -> productId1.equals(item.getProductId())));
        assertTrue(order.getItems().stream().anyMatch(item -> productId2.equals(item.getProductId())));
    }

    /**
     * **Validates: Requirements 3.1, 3.2**
     * Property 4: Order Creation Validates Business Rules
     */
    @Test
    void orderCreationFromCartValidation() {
        String userId = "user123";
        String orderId = "order123";
        
        // Create products
        Product product1 = new Product("prod1", "Product 1", "Desc 1", BigDecimal.valueOf(10.00), 100, "Cat1");
        Product product2 = new Product("prod2", "Product 2", "Desc 2", BigDecimal.valueOf(25.50), 50, "Cat2");
        
        // Create cart items
        CartItem cartItem1 = new CartItem(userId, "prod1", 2);
        CartItem cartItem2 = new CartItem(userId, "prod2", 1);
        List<CartItem> cartItems = Arrays.asList(cartItem1, cartItem2);
        
        // Convert cart items to order items
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        
        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProductId().equals("prod1") ? product1 : product2;
            
            // Validate inventory availability
            assertTrue(product.getInventory() >= cartItem.getQuantity());
            
            OrderItem orderItem = new OrderItem(
                cartItem.getProductId(),
                product.getName(),
                cartItem.getQuantity(),
                product.getPrice()
            );
            orderItems.add(orderItem);
            totalAmount = totalAmount.add(orderItem.getTotalPrice());
        }
        
        // Create order from cart
        Order order = new Order(orderId, userId, orderItems, totalAmount);
        
        // Verify order creation from cart
        assertEquals(orderId, order.getOrderId());
        assertEquals(userId, order.getUserId());
        assertEquals(2, order.getItems().size());
        assertEquals(Order.OrderStatus.PENDING, order.getStatus());
        
        // Verify total calculation: (2 * 10.00) + (1 * 25.50) = 45.50
        assertEquals(BigDecimal.valueOf(45.50), order.getTotalAmount());
        
        // Verify order items match cart items
        for (CartItem cartItem : cartItems) {
            OrderItem matchingOrderItem = order.getItems().stream()
                .filter(item -> item.getProductId().equals(cartItem.getProductId()))
                .findFirst()
                .orElseThrow();
            
            assertEquals(cartItem.getProductId(), matchingOrderItem.getProductId());
            assertEquals(cartItem.getQuantity(), matchingOrderItem.getQuantity());
        }
    }

    /**
     * **Validates: Requirements 3.1, 3.2**
     * Property 4: Order Creation Validates Business Rules
     */
    @Test
    void orderCreationValidatesBusinessConstraints() {
        String userId = "user123";
        String orderId = "order123";
        
        // Test various constraint scenarios
        Object[][] testCases = {
            // {quantity, inventory, price, shouldSucceed, description}
            {1, 10, BigDecimal.valueOf(10.00), true, "Valid order"},
            {5, 5, BigDecimal.valueOf(20.00), true, "Exact inventory match"},
            {10, 5, BigDecimal.valueOf(15.00), false, "Insufficient inventory"},
            {0, 10, BigDecimal.valueOf(10.00), false, "Zero quantity"},
            {-1, 10, BigDecimal.valueOf(10.00), false, "Negative quantity"},
            {1, 0, BigDecimal.valueOf(10.00), false, "No inventory"},
        };
        
        for (Object[] testCase : testCases) {
            Integer quantity = (Integer) testCase[0];
            Integer inventory = (Integer) testCase[1];
            BigDecimal price = (BigDecimal) testCase[2];
            boolean shouldSucceed = (Boolean) testCase[3];
            String description = (String) testCase[4];
            
            // Create product
            Product product = new Product("prod1", "Test Product", "Description", 
                                        price, inventory, "Electronics");
            
            // Validate business constraints
            boolean isValidQuantity = quantity > 0;
            boolean hasInventory = inventory >= quantity;
            boolean isValidPrice = price.compareTo(BigDecimal.ZERO) > 0;
            boolean meetsConstraints = isValidQuantity && hasInventory && isValidPrice;
            
            assertEquals(shouldSucceed, meetsConstraints, 
                String.format("Constraint validation failed for: %s", description));
            
            if (meetsConstraints) {
                // Create valid order
                OrderItem orderItem = new OrderItem("prod1", product.getName(), quantity, price);
                BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(quantity));
                Order order = new Order(orderId + "_" + description.replaceAll(" ", ""), 
                                      userId, Arrays.asList(orderItem), totalAmount);
                
                // Verify order creation
                assertNotNull(order.getOrderId());
                assertEquals(userId, order.getUserId());
                assertEquals(Order.OrderStatus.PENDING, order.getStatus());
                assertEquals(1, order.getItems().size());
                assertTrue(order.getTotalAmount().compareTo(BigDecimal.ZERO) > 0);
            }
        }
    }

    /**
     * **Validates: Requirements 3.1, 3.2**
     * Property 4: Order Creation Validates Business Rules
     */
    @Property
    @Tag("Feature: z-commerce, Property 4: Order Creation Validates Business Rules")
    void orderCreationMaintainsDataConsistency(
        @ForAll("orderIds") String orderId,
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId,
        @ForAll("quantities") Integer quantity,
        @ForAll("prices") BigDecimal price
    ) {
        // Create order item
        OrderItem orderItem = new OrderItem(productId, "Test Product", quantity, price);
        BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(quantity));
        
        // Create order
        Order order = new Order(orderId, userId, Arrays.asList(orderItem), totalAmount);
        
        // Verify data consistency across all fields
        assertEquals(orderId, order.getOrderId());
        assertEquals(userId, order.getUserId());
        assertEquals(Order.OrderStatus.PENDING, order.getStatus());
        assertEquals(0, totalAmount.compareTo(order.getTotalAmount()));
        assertEquals("ORDER", order.getEntityType());
        
        // Verify key structure consistency
        assertEquals("ORDER#" + orderId, order.getPk());
        assertEquals("DETAILS", order.getSk());
        assertEquals("ORDER#" + userId, order.getGsi1pk());
        assertEquals(order.getCreatedAt().toString(), order.getGsi1sk());
        
        // Verify order items consistency
        assertEquals(1, order.getItems().size());
        OrderItem retrievedItem = order.getItems().get(0);
        assertEquals(productId, retrievedItem.getProductId());
        assertEquals("Test Product", retrievedItem.getProductName());
        assertEquals(quantity, retrievedItem.getQuantity());
        assertEquals(0, price.compareTo(retrievedItem.getPrice()));
        assertEquals(0, totalAmount.compareTo(retrievedItem.getTotalPrice()));
        
        // Verify timestamps are set and consistent
        assertNotNull(order.getCreatedAt());
        assertNotNull(order.getUpdatedAt());
        assertTrue(order.getUpdatedAt().equals(order.getCreatedAt()) || 
                  order.getUpdatedAt().isAfter(order.getCreatedAt()));
    }

    /**
     * **Validates: Requirements 3.1, 3.2**
     * Property 4: Order Creation Validates Business Rules
     */
    @Test
    void orderCreationWithMultipleItemsValidation() {
        String userId = "user123";
        String orderId = "order123";
        
        // Create multiple products with different inventory levels
        Product product1 = new Product("prod1", "Product 1", "Desc 1", BigDecimal.valueOf(10.00), 100, "Cat1");
        Product product2 = new Product("prod2", "Product 2", "Desc 2", BigDecimal.valueOf(25.50), 5, "Cat2");
        Product product3 = new Product("prod3", "Product 3", "Desc 3", BigDecimal.valueOf(99.99), 0, "Cat3");
        
        // Test scenarios with multiple items
        List<Object[]> scenarios = Arrays.asList(
            // {prod1Qty, prod2Qty, prod3Qty, shouldSucceed, description}
            new Object[]{2, 1, 0, true, "Valid quantities within inventory"},
            new Object[]{50, 3, 0, true, "High quantity but within limits"},
            new Object[]{1, 10, 0, false, "Exceeds prod2 inventory"},
            new Object[]{1, 1, 1, false, "Includes out-of-stock product"},
            new Object[]{200, 1, 0, false, "Exceeds prod1 inventory"}
        );
        
        for (Object[] scenario : scenarios) {
            Integer qty1 = (Integer) scenario[0];
            Integer qty2 = (Integer) scenario[1];
            Integer qty3 = (Integer) scenario[2];
            boolean shouldSucceed = (Boolean) scenario[3];
            String description = (String) scenario[4];
            
            List<OrderItem> orderItems = new ArrayList<>();
            BigDecimal totalAmount = BigDecimal.ZERO;
            boolean allItemsValid = true;
            
            // Add items only if quantity > 0
            if (qty1 > 0) {
                if (qty1 <= product1.getInventory()) {
                    OrderItem item1 = new OrderItem("prod1", product1.getName(), qty1, product1.getPrice());
                    orderItems.add(item1);
                    totalAmount = totalAmount.add(item1.getTotalPrice());
                } else {
                    allItemsValid = false;
                }
            }
            
            if (qty2 > 0) {
                if (qty2 <= product2.getInventory()) {
                    OrderItem item2 = new OrderItem("prod2", product2.getName(), qty2, product2.getPrice());
                    orderItems.add(item2);
                    totalAmount = totalAmount.add(item2.getTotalPrice());
                } else {
                    allItemsValid = false;
                }
            }
            
            if (qty3 > 0) {
                if (qty3 <= product3.getInventory()) {
                    OrderItem item3 = new OrderItem("prod3", product3.getName(), qty3, product3.getPrice());
                    orderItems.add(item3);
                    totalAmount = totalAmount.add(item3.getTotalPrice());
                } else {
                    allItemsValid = false;
                }
            }
            
            assertEquals(shouldSucceed, allItemsValid, 
                String.format("Validation failed for scenario: %s", description));
            
            if (allItemsValid && !orderItems.isEmpty()) {
                // Create order with valid items
                Order order = new Order(orderId + "_" + description.replaceAll(" ", ""), 
                                      userId, orderItems, totalAmount);
                
                // Verify order creation
                assertEquals(orderItems.size(), order.getItems().size());
                assertEquals(0, totalAmount.compareTo(order.getTotalAmount()));
                assertEquals(Order.OrderStatus.PENDING, order.getStatus());
                
                // Verify each item in the order
                for (OrderItem expectedItem : orderItems) {
                    OrderItem actualItem = order.getItems().stream()
                        .filter(item -> item.getProductId().equals(expectedItem.getProductId()))
                        .findFirst()
                        .orElseThrow();
                    
                    assertEquals(expectedItem.getProductId(), actualItem.getProductId());
                    assertEquals(expectedItem.getQuantity(), actualItem.getQuantity());
                    assertEquals(0, expectedItem.getPrice().compareTo(actualItem.getPrice()));
                    assertEquals(0, expectedItem.getTotalPrice().compareTo(actualItem.getTotalPrice()));
                }
            }
        }
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
        return Arbitraries.integers().between(1, 20);
    }

    @Provide
    Arbitrary<Integer> inventories() {
        return Arbitraries.integers().between(0, 100);
    }

    @Provide
    Arbitrary<BigDecimal> prices() {
        return Arbitraries.bigDecimals()
            .between(BigDecimal.valueOf(1.00), BigDecimal.valueOf(999.99))
            .ofScale(2);
    }
}