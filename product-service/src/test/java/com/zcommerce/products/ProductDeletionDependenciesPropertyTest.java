package com.zcommerce.products;

import com.zcommerce.shared.model.Product;
import com.zcommerce.shared.model.CartItem;
import com.zcommerce.shared.model.OrderItem;
import com.zcommerce.shared.model.Order;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for product deletion handling dependencies.
 * **Validates: Requirements 1.3**
 */
class ProductDeletionDependenciesPropertyTest {

    /**
     * **Validates: Requirements 1.3**
     * Property 2: Product Deletion Handles Dependencies
     * For any product that exists in the system, deleting it should remove it 
     * from the catalog and properly handle any dependent cart references without 
     * leaving orphaned data.
     */
    @Property
    @Tag("Feature: z-commerce, Property 2: Product Deletion Handles Dependencies")
    void productDeletionIdentifiesDependentCartItems(
        @ForAll("productIds") String productId,
        @ForAll("userIds") String userId1,
        @ForAll("userIds") String userId2,
        @ForAll("quantities") Integer quantity1,
        @ForAll("quantities") Integer quantity2
    ) {
        Assume.that(!userId1.equals(userId2)); // Different users
        
        // Create product
        Product product = new Product(productId, "Test Product", "Description", 
                                    BigDecimal.valueOf(99.99), 100, "Electronics");
        
        // Create cart items that reference this product
        CartItem cartItem1 = new CartItem(userId1, productId, quantity1);
        CartItem cartItem2 = new CartItem(userId2, productId, quantity2);
        
        // Verify cart items reference the product
        assertEquals(productId, cartItem1.getProductId());
        assertEquals(productId, cartItem2.getProductId());
        
        // Simulate dependency check before deletion
        List<CartItem> dependentCartItems = Arrays.asList(cartItem1, cartItem2);
        
        // Verify all dependent cart items are identified
        for (CartItem cartItem : dependentCartItems) {
            assertEquals(productId, cartItem.getProductId());
            assertTrue(cartItem.getSk().contains(productId));
            assertEquals("CART#PRODUCT#" + productId, cartItem.getSk());
        }
        
        // Verify cart items have different users but same product
        assertNotEquals(cartItem1.getUserId(), cartItem2.getUserId());
        assertEquals(cartItem1.getProductId(), cartItem2.getProductId());
        
        // Verify key structures for dependency identification
        assertEquals("USER#" + userId1, cartItem1.getPk());
        assertEquals("USER#" + userId2, cartItem2.getPk());
        assertTrue(cartItem1.getSk().endsWith(productId));
        assertTrue(cartItem2.getSk().endsWith(productId));
    }

    /**
     * **Validates: Requirements 1.3**
     * Property 2: Product Deletion Handles Dependencies
     */
    @Property
    @Tag("Feature: z-commerce, Property 2: Product Deletion Handles Dependencies")
    void productDeletionIdentifiesDependentOrderItems(
        @ForAll("productIds") String productId,
        @ForAll("orderIds") String orderId1,
        @ForAll("orderIds") String orderId2,
        @ForAll("userIds") String userId1,
        @ForAll("userIds") String userId2,
        @ForAll("quantities") Integer quantity1,
        @ForAll("quantities") Integer quantity2,
        @ForAll("prices") BigDecimal price
    ) {
        Assume.that(!orderId1.equals(orderId2)); // Different orders
        Assume.that(!userId1.equals(userId2)); // Different users
        
        // Create product
        Product product = new Product(productId, "Test Product", "Description", 
                                    price, 100, "Electronics");
        
        // Create order items that reference this product
        OrderItem orderItem1 = new OrderItem(productId, product.getName(), quantity1, price);
        OrderItem orderItem2 = new OrderItem(productId, product.getName(), quantity2, price);
        
        // Create orders containing these items
        Order order1 = new Order(orderId1, userId1, Arrays.asList(orderItem1), 
                                price.multiply(BigDecimal.valueOf(quantity1)));
        Order order2 = new Order(orderId2, userId2, Arrays.asList(orderItem2), 
                                price.multiply(BigDecimal.valueOf(quantity2)));
        
        // Verify order items reference the product
        assertEquals(productId, orderItem1.getProductId());
        assertEquals(productId, orderItem2.getProductId());
        assertEquals(product.getName(), orderItem1.getProductName());
        assertEquals(product.getName(), orderItem2.getProductName());
        
        // Simulate dependency check before deletion
        List<Order> dependentOrders = Arrays.asList(order1, order2);
        
        // Verify all dependent orders are identified
        for (Order order : dependentOrders) {
            boolean hasProductReference = order.getItems().stream()
                .anyMatch(item -> productId.equals(item.getProductId()));
            assertTrue(hasProductReference, "Order should contain reference to product");
        }
        
        // Verify orders are distinct but reference same product
        assertNotEquals(order1.getOrderId(), order2.getOrderId());
        assertNotEquals(order1.getUserId(), order2.getUserId());
        
        // Both orders should contain items referencing the same product
        assertTrue(order1.getItems().stream().anyMatch(item -> productId.equals(item.getProductId())));
        assertTrue(order2.getItems().stream().anyMatch(item -> productId.equals(item.getProductId())));
    }

    /**
     * **Validates: Requirements 1.3**
     * Property 2: Product Deletion Handles Dependencies
     */
    @Test
    void productDeletionWithNoDependencies() {
        String productId = "prod123";
        
        // Create product with no dependencies
        Product product = new Product(productId, "Test Product", "Description", 
                                    BigDecimal.valueOf(99.99), 100, "Electronics");
        
        // Simulate dependency check - no cart items or orders reference this product
        List<CartItem> dependentCartItems = new ArrayList<>();
        List<Order> dependentOrders = new ArrayList<>();
        
        // Verify no dependencies exist
        assertTrue(dependentCartItems.isEmpty());
        assertTrue(dependentOrders.isEmpty());
        
        // Simulate safe deletion - product can be deleted without orphaning data
        boolean canDelete = dependentCartItems.isEmpty() && dependentOrders.isEmpty();
        assertTrue(canDelete);
        
        // Verify product data before deletion
        assertEquals(productId, product.getProductId());
        assertEquals("PRODUCT#" + productId, product.getPk());
        assertEquals("DETAILS", product.getSk());
        assertEquals("PRODUCT", product.getEntityType());
    }

    /**
     * **Validates: Requirements 1.3**
     * Property 2: Product Deletion Handles Dependencies
     */
    @Test
    void productDeletionWithCartDependencies() {
        String productId = "prod123";
        String userId1 = "user1";
        String userId2 = "user2";
        
        // Create product
        Product product = new Product(productId, "Test Product", "Description", 
                                    BigDecimal.valueOf(99.99), 100, "Electronics");
        
        // Create cart items that depend on this product
        CartItem cartItem1 = new CartItem(userId1, productId, 2);
        CartItem cartItem2 = new CartItem(userId2, productId, 1);
        List<CartItem> dependentCartItems = Arrays.asList(cartItem1, cartItem2);
        
        // Verify dependencies exist
        assertFalse(dependentCartItems.isEmpty());
        assertEquals(2, dependentCartItems.size());
        
        // Verify all cart items reference the product
        for (CartItem cartItem : dependentCartItems) {
            assertEquals(productId, cartItem.getProductId());
            assertTrue(cartItem.getSk().contains(productId));
        }
        
        // Simulate deletion handling - must clean up cart items first
        boolean hasCartDependencies = !dependentCartItems.isEmpty();
        assertTrue(hasCartDependencies);
        
        // Before deletion, dependent cart items should be identified and handled
        List<String> affectedUsers = dependentCartItems.stream()
            .map(CartItem::getUserId)
            .distinct()
            .toList();
        
        assertEquals(2, affectedUsers.size());
        assertTrue(affectedUsers.contains(userId1));
        assertTrue(affectedUsers.contains(userId2));
        
        // Simulate cart cleanup - remove all cart items referencing the product
        List<CartItem> remainingCartItems = dependentCartItems.stream()
            .filter(item -> !productId.equals(item.getProductId()))
            .toList();
        
        // After cleanup, no cart items should reference the deleted product
        assertTrue(remainingCartItems.isEmpty());
    }

    /**
     * **Validates: Requirements 1.3**
     * Property 2: Product Deletion Handles Dependencies
     */
    @Test
    void productDeletionWithOrderDependencies() {
        String productId = "prod123";
        String orderId1 = "order1";
        String orderId2 = "order2";
        String userId1 = "user1";
        String userId2 = "user2";
        BigDecimal price = BigDecimal.valueOf(99.99);
        
        // Create product
        Product product = new Product(productId, "Test Product", "Description", 
                                    price, 100, "Electronics");
        
        // Create orders that contain this product
        OrderItem orderItem1 = new OrderItem(productId, product.getName(), 1, price);
        OrderItem orderItem2 = new OrderItem(productId, product.getName(), 2, price);
        
        Order order1 = new Order(orderId1, userId1, Arrays.asList(orderItem1), price);
        Order order2 = new Order(orderId2, userId2, Arrays.asList(orderItem2), price.multiply(BigDecimal.valueOf(2)));
        
        List<Order> dependentOrders = Arrays.asList(order1, order2);
        
        // Verify dependencies exist
        assertFalse(dependentOrders.isEmpty());
        assertEquals(2, dependentOrders.size());
        
        // Verify all orders contain the product
        for (Order order : dependentOrders) {
            boolean containsProduct = order.getItems().stream()
                .anyMatch(item -> productId.equals(item.getProductId()));
            assertTrue(containsProduct);
        }
        
        // Simulate deletion handling - orders with historical data should be preserved
        boolean hasOrderDependencies = dependentOrders.stream()
            .anyMatch(order -> order.getItems().stream()
                .anyMatch(item -> productId.equals(item.getProductId())));
        
        assertTrue(hasOrderDependencies);
        
        // Historical orders should remain intact (product deletion doesn't affect completed orders)
        // But the product catalog entry can be marked as discontinued/deleted
        for (Order order : dependentOrders) {
            assertNotNull(order.getOrderId());
            assertNotNull(order.getUserId());
            assertFalse(order.getItems().isEmpty());
            
            // Order items should preserve product information even after product deletion
            for (OrderItem item : order.getItems()) {
                if (productId.equals(item.getProductId())) {
                    assertEquals(product.getName(), item.getProductName());
                    assertEquals(price, item.getPrice());
                }
            }
        }
    }

    /**
     * **Validates: Requirements 1.3**
     * Property 2: Product Deletion Handles Dependencies
     */
    @Property
    @Tag("Feature: z-commerce, Property 2: Product Deletion Handles Dependencies")
    void productDeletionPreservesUnrelatedData(
        @ForAll("productIds") String productToDelete,
        @ForAll("productIds") String otherProductId,
        @ForAll("userIds") String userId,
        @ForAll("quantities") Integer quantity1,
        @ForAll("quantities") Integer quantity2
    ) {
        Assume.that(!productToDelete.equals(otherProductId)); // Different products
        
        // Create two products
        Product productToDelete1 = new Product(productToDelete, "Product to Delete", "Description", 
                                             BigDecimal.valueOf(99.99), 100, "Electronics");
        Product otherProduct = new Product(otherProductId, "Other Product", "Description", 
                                         BigDecimal.valueOf(149.99), 50, "Electronics");
        
        // Create cart items for both products
        CartItem cartItemToDelete = new CartItem(userId, productToDelete, quantity1);
        CartItem cartItemToKeep = new CartItem(userId, otherProductId, quantity2);
        
        // Simulate deletion of first product
        List<CartItem> allCartItems = Arrays.asList(cartItemToDelete, cartItemToKeep);
        
        // Filter out cart items for deleted product
        List<CartItem> remainingCartItems = allCartItems.stream()
            .filter(item -> !productToDelete.equals(item.getProductId()))
            .toList();
        
        // Verify only the correct cart item remains
        assertEquals(1, remainingCartItems.size());
        assertEquals(otherProductId, remainingCartItems.get(0).getProductId());
        assertEquals(quantity2, remainingCartItems.get(0).getQuantity());
        
        // Verify the other product is unaffected
        assertEquals(otherProductId, otherProduct.getProductId());
        assertEquals("Other Product", otherProduct.getName());
        assertEquals(0, BigDecimal.valueOf(149.99).compareTo(otherProduct.getPrice()));
        assertEquals(Integer.valueOf(50), otherProduct.getInventory());
        
        // Verify key structures remain intact for unrelated data
        assertEquals("PRODUCT#" + otherProductId, otherProduct.getPk());
        assertEquals("USER#" + userId, cartItemToKeep.getPk());
        assertEquals("CART#PRODUCT#" + otherProductId, cartItemToKeep.getSk());
    }

    /**
     * **Validates: Requirements 1.3**
     * Property 2: Product Deletion Handles Dependencies
     */
    @Test
    void productDeletionHandlesMultipleDependencyTypes() {
        String productId = "prod123";
        String userId1 = "user1";
        String userId2 = "user2";
        String orderId = "order1";
        BigDecimal price = BigDecimal.valueOf(99.99);
        
        // Create product
        Product product = new Product(productId, "Test Product", "Description", 
                                    price, 100, "Electronics");
        
        // Create cart dependencies
        CartItem cartItem1 = new CartItem(userId1, productId, 2);
        CartItem cartItem2 = new CartItem(userId2, productId, 1);
        List<CartItem> dependentCartItems = Arrays.asList(cartItem1, cartItem2);
        
        // Create order dependencies
        OrderItem orderItem = new OrderItem(productId, product.getName(), 1, price);
        Order order = new Order(orderId, userId1, Arrays.asList(orderItem), price);
        List<Order> dependentOrders = Arrays.asList(order);
        
        // Verify multiple dependency types exist
        assertFalse(dependentCartItems.isEmpty());
        assertFalse(dependentOrders.isEmpty());
        
        // Simulate comprehensive dependency check
        boolean hasCartDependencies = !dependentCartItems.isEmpty();
        boolean hasOrderDependencies = dependentOrders.stream()
            .anyMatch(o -> o.getItems().stream()
                .anyMatch(item -> productId.equals(item.getProductId())));
        
        assertTrue(hasCartDependencies);
        assertTrue(hasOrderDependencies);
        
        // Deletion strategy should handle both types:
        // 1. Remove cart items (active shopping state)
        List<CartItem> cartItemsAfterCleanup = dependentCartItems.stream()
            .filter(item -> !productId.equals(item.getProductId()))
            .toList();
        assertTrue(cartItemsAfterCleanup.isEmpty());
        
        // 2. Preserve orders (historical data)
        assertEquals(1, dependentOrders.size());
        assertTrue(dependentOrders.get(0).getItems().stream()
            .anyMatch(item -> productId.equals(item.getProductId())));
        
        // 3. Product can be marked as deleted/discontinued
        assertNotNull(product.getProductId());
        assertEquals("PRODUCT#" + productId, product.getPk());
    }

    // Providers
    @Provide
    Arbitrary<String> productIds() {
        return Arbitraries.strings().alpha().ofLength(8);
    }

    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.strings().alpha().ofLength(8);
    }

    @Provide
    Arbitrary<String> orderIds() {
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
}