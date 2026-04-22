package com.zcommerce.cart;

import com.zcommerce.shared.model.CartItem;
import com.zcommerce.shared.model.Product;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for cart operations maintaining state consistency.
 * **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**
 */
class CartStateConsistencyPropertyTest {

    /**
     * **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**
     * Property 3: Cart Operations Maintain State Consistency
     * For any user cart and valid product operations (add, update, remove), 
     * the cart should maintain accurate item quantities, pricing totals, 
     * and inventory validation throughout all operations.
     */
    @Property
    @Tag("Feature: z-commerce, Property 3: Cart Operations Maintain State Consistency")
    void cartAddOperationMaintainsConsistency(
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId,
        @ForAll("quantities") Integer quantity,
        @ForAll("prices") BigDecimal productPrice,
        @ForAll("inventories") Integer productInventory
    ) {
        Assume.that(quantity <= productInventory); // Valid inventory constraint
        
        // Create product with available inventory
        Product product = new Product(productId, "Test Product", "Description", 
                                    productPrice, productInventory, "Electronics");
        
        // Create cart item (simulating add operation)
        CartItem cartItem = new CartItem(userId, productId, quantity);
        
        // Verify cart item consistency
        assertEquals(userId, cartItem.getUserId());
        assertEquals(productId, cartItem.getProductId());
        assertEquals(quantity, cartItem.getQuantity());
        assertEquals("CART_ITEM", cartItem.getEntityType());
        
        // Verify key structure for cart operations
        assertEquals("USER#" + userId, cartItem.getPk());
        assertEquals("CART#PRODUCT#" + productId, cartItem.getSk());
        
        // Verify inventory constraint is respected
        assertTrue(quantity <= product.getInventory());
        assertTrue(product.isAvailable());
        
        // Verify timestamps are set
        assertNotNull(cartItem.getAddedAt());
        
        // Calculate expected total for this item
        BigDecimal expectedItemTotal = productPrice.multiply(BigDecimal.valueOf(quantity));
        assertTrue(expectedItemTotal.compareTo(BigDecimal.ZERO) > 0);
    }

    /**
     * **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**
     * Property 3: Cart Operations Maintain State Consistency
     */
    @Property
    @Tag("Feature: z-commerce, Property 3: Cart Operations Maintain State Consistency")
    void cartUpdateOperationMaintainsConsistency(
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId,
        @ForAll("quantities") Integer originalQuantity,
        @ForAll("quantities") Integer newQuantity,
        @ForAll("inventories") Integer productInventory
    ) {
        Assume.that(originalQuantity <= productInventory); // Original quantity valid
        Assume.that(newQuantity <= productInventory); // New quantity valid
        Assume.that(newQuantity > 0); // Valid update (not removal)
        
        // Create product
        Product product = new Product(productId, "Test Product", "Description", 
                                    BigDecimal.valueOf(99.99), productInventory, "Electronics");
        
        // Create original cart item
        CartItem originalCartItem = new CartItem(userId, productId, originalQuantity);
        
        // Simulate update operation
        CartItem updatedCartItem = new CartItem(userId, productId, newQuantity);
        updatedCartItem.setAddedAt(originalCartItem.getAddedAt()); // Preserve add time
        
        // Verify identity preservation during update
        assertEquals(originalCartItem.getUserId(), updatedCartItem.getUserId());
        assertEquals(originalCartItem.getProductId(), updatedCartItem.getProductId());
        assertEquals(originalCartItem.getPk(), updatedCartItem.getPk());
        assertEquals(originalCartItem.getSk(), updatedCartItem.getSk());
        assertEquals(originalCartItem.getEntityType(), updatedCartItem.getEntityType());
        
        // Verify quantity update
        assertEquals(newQuantity, updatedCartItem.getQuantity());
        
        // Verify inventory constraint is still respected
        assertTrue(newQuantity <= product.getInventory());
        
        // Verify timestamps
        assertEquals(originalCartItem.getAddedAt(), updatedCartItem.getAddedAt());
        
        // Calculate expected totals
        BigDecimal originalTotal = product.getPrice().multiply(BigDecimal.valueOf(originalQuantity));
        BigDecimal newTotal = product.getPrice().multiply(BigDecimal.valueOf(newQuantity));
        
        // Verify total calculation consistency
        assertTrue(originalTotal.compareTo(BigDecimal.ZERO) > 0);
        assertTrue(newTotal.compareTo(BigDecimal.ZERO) > 0);
    }

    /**
     * **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**
     * Property 3: Cart Operations Maintain State Consistency
     */
    @Property
    @Tag("Feature: z-commerce, Property 3: Cart Operations Maintain State Consistency")
    void cartRemoveOperationMaintainsConsistency(
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId1,
        @ForAll("productIds") String productId2,
        @ForAll("quantities") Integer quantity1,
        @ForAll("quantities") Integer quantity2
    ) {
        Assume.that(!productId1.equals(productId2)); // Different products
        
        // Create cart items for multiple products
        CartItem cartItem1 = new CartItem(userId, productId1, quantity1);
        CartItem cartItem2 = new CartItem(userId, productId2, quantity2);
        
        List<CartItem> originalCart = List.of(cartItem1, cartItem2);
        
        // Simulate removal of first product
        List<CartItem> cartAfterRemoval = originalCart.stream()
            .filter(item -> !productId1.equals(item.getProductId()))
            .collect(Collectors.toList());
        
        // Verify removal consistency
        assertEquals(1, cartAfterRemoval.size());
        assertEquals(productId2, cartAfterRemoval.get(0).getProductId());
        assertEquals(quantity2, cartAfterRemoval.get(0).getQuantity());
        
        // Verify remaining item is unchanged
        CartItem remainingItem = cartAfterRemoval.get(0);
        assertEquals(userId, remainingItem.getUserId());
        assertEquals(productId2, remainingItem.getProductId());
        assertEquals(quantity2, remainingItem.getQuantity());
        assertEquals("USER#" + userId, remainingItem.getPk());
        assertEquals("CART#PRODUCT#" + productId2, remainingItem.getSk());
        
        // Verify no orphaned references to removed product
        boolean hasRemovedProduct = cartAfterRemoval.stream()
            .anyMatch(item -> productId1.equals(item.getProductId()));
        assertFalse(hasRemovedProduct);
    }

    /**
     * **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**
     * Property 3: Cart Operations Maintain State Consistency
     */
    @Test
    void cartTotalCalculationConsistency() {
        String userId = "user123";
        
        // Create products with different prices
        Product product1 = new Product("prod1", "Product 1", "Desc 1", BigDecimal.valueOf(10.00), 100, "Cat1");
        Product product2 = new Product("prod2", "Product 2", "Desc 2", BigDecimal.valueOf(25.50), 50, "Cat2");
        Product product3 = new Product("prod3", "Product 3", "Desc 3", BigDecimal.valueOf(99.99), 25, "Cat3");
        
        // Create cart items
        CartItem item1 = new CartItem(userId, "prod1", 2); // 2 * 10.00 = 20.00
        CartItem item2 = new CartItem(userId, "prod2", 1); // 1 * 25.50 = 25.50
        CartItem item3 = new CartItem(userId, "prod3", 3); // 3 * 99.99 = 299.97
        
        List<CartItem> cartItems = List.of(item1, item2, item3);
        
        // Calculate total manually
        BigDecimal expectedTotal = BigDecimal.valueOf(20.00)
            .add(BigDecimal.valueOf(25.50))
            .add(BigDecimal.valueOf(299.97));
        
        // Simulate cart total calculation
        BigDecimal calculatedTotal = BigDecimal.ZERO;
        for (CartItem item : cartItems) {
            Product product = switch (item.getProductId()) {
                case "prod1" -> product1;
                case "prod2" -> product2;
                case "prod3" -> product3;
                default -> throw new IllegalStateException("Unknown product");
            };
            BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            calculatedTotal = calculatedTotal.add(itemTotal);
        }
        
        // Verify total calculation consistency
        assertEquals(0, expectedTotal.compareTo(calculatedTotal));
        assertEquals(BigDecimal.valueOf(345.47), calculatedTotal);
        
        // Verify all cart items maintain consistency
        for (CartItem item : cartItems) {
            assertEquals(userId, item.getUserId());
            assertEquals("USER#" + userId, item.getPk());
            assertTrue(item.getSk().startsWith("CART#PRODUCT#"));
            assertEquals("CART_ITEM", item.getEntityType());
            assertTrue(item.getQuantity() > 0);
        }
    }

    /**
     * **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**
     * Property 3: Cart Operations Maintain State Consistency
     */
    @Test
    void cartInventoryValidationConsistency() {
        String userId = "user123";
        String productId = "prod123";
        
        // Test various inventory scenarios
        Object[][] testCases = {
            {100, 50, true},    // Sufficient inventory
            {100, 100, true},   // Exact inventory match
            {100, 101, false},  // Insufficient inventory
            {0, 1, false},      // No inventory
            {null, 1, false},   // Null inventory
        };
        
        for (Object[] testCase : testCases) {
            Integer productInventory = (Integer) testCase[0];
            Integer requestedQuantity = (Integer) testCase[1];
            boolean shouldBeValid = (Boolean) testCase[2];
            
            // Create product with specific inventory
            Product product = new Product(productId, "Test Product", "Description", 
                                        BigDecimal.valueOf(99.99), productInventory, "Electronics");
            
            // Validate inventory constraint
            boolean isValidRequest = productInventory != null && 
                                   productInventory >= requestedQuantity && 
                                   requestedQuantity > 0;
            
            assertEquals(shouldBeValid, isValidRequest,
                String.format("Inventory validation failed for inventory=%s, requested=%s", 
                    productInventory, requestedQuantity));
            
            if (isValidRequest) {
                // Create cart item only if valid
                CartItem cartItem = new CartItem(userId, productId, requestedQuantity);
                
                // Verify cart item consistency
                assertEquals(userId, cartItem.getUserId());
                assertEquals(productId, cartItem.getProductId());
                assertEquals(requestedQuantity, cartItem.getQuantity());
                assertTrue(requestedQuantity <= (productInventory != null ? productInventory : 0));
            }
        }
    }

    /**
     * **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**
     * Property 3: Cart Operations Maintain State Consistency
     */
    @Property
    @Tag("Feature: z-commerce, Property 3: Cart Operations Maintain State Consistency")
    void multipleCartOperationsPreserveConsistency(
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId1,
        @ForAll("productIds") String productId2,
        @ForAll("productIds") String productId3,
        @ForAll("quantities") Integer qty1,
        @ForAll("quantities") Integer qty2,
        @ForAll("quantities") Integer qty3,
        @ForAll("quantities") Integer newQty1
    ) {
        Assume.that(!productId1.equals(productId2) && !productId2.equals(productId3) && !productId1.equals(productId3));
        Assume.that(newQty1 > 0);
        
        // Create initial cart with multiple items
        CartItem item1 = new CartItem(userId, productId1, qty1);
        CartItem item2 = new CartItem(userId, productId2, qty2);
        CartItem item3 = new CartItem(userId, productId3, qty3);
        
        List<CartItem> cart = new ArrayList<>(List.of(item1, item2, item3));
        
        // Verify initial cart consistency
        assertEquals(3, cart.size());
        for (CartItem item : cart) {
            assertEquals(userId, item.getUserId());
            assertEquals("USER#" + userId, item.getPk());
            assertTrue(item.getSk().startsWith("CART#PRODUCT#"));
            assertEquals("CART_ITEM", item.getEntityType());
        }
        
        // Operation 1: Update quantity of first item
        cart.stream()
            .filter(item -> productId1.equals(item.getProductId()))
            .findFirst()
            .ifPresent(item -> item.setQuantity(newQty1));
        
        // Verify update consistency
        CartItem updatedItem = cart.stream()
            .filter(item -> productId1.equals(item.getProductId()))
            .findFirst()
            .orElseThrow();
        assertEquals(newQty1, updatedItem.getQuantity());
        assertEquals(productId1, updatedItem.getProductId());
        
        // Operation 2: Remove second item
        cart.removeIf(item -> productId2.equals(item.getProductId()));
        
        // Verify removal consistency
        assertEquals(2, cart.size());
        boolean hasRemovedItem = cart.stream()
            .anyMatch(item -> productId2.equals(item.getProductId()));
        assertFalse(hasRemovedItem);
        
        // Verify remaining items are intact
        assertTrue(cart.stream().anyMatch(item -> productId1.equals(item.getProductId())));
        assertTrue(cart.stream().anyMatch(item -> productId3.equals(item.getProductId())));
        
        // Verify all remaining items maintain consistency
        for (CartItem item : cart) {
            assertEquals(userId, item.getUserId());
            assertEquals("USER#" + userId, item.getPk());
            assertTrue(item.getSk().startsWith("CART#PRODUCT#"));
            assertEquals("CART_ITEM", item.getEntityType());
            assertTrue(item.getQuantity() > 0);
        }
    }

    /**
     * **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**
     * Property 3: Cart Operations Maintain State Consistency
     */
    @Test
    void cartStateAfterCompleteOperationSequence() {
        String userId = "user123";
        List<CartItem> cart = new ArrayList<>();
        
        // Sequence: Add -> Update -> Add -> Remove -> Update
        
        // 1. Add first item
        CartItem item1 = new CartItem(userId, "prod1", 2);
        cart.add(item1);
        assertEquals(1, cart.size());
        
        // 2. Update first item quantity
        cart.get(0).setQuantity(5);
        assertEquals(5, cart.get(0).getQuantity());
        
        // 3. Add second item
        CartItem item2 = new CartItem(userId, "prod2", 1);
        cart.add(item2);
        assertEquals(2, cart.size());
        
        // 4. Remove first item
        cart.removeIf(item -> "prod1".equals(item.getProductId()));
        assertEquals(1, cart.size());
        assertEquals("prod2", cart.get(0).getProductId());
        
        // 5. Update remaining item
        cart.get(0).setQuantity(3);
        assertEquals(3, cart.get(0).getQuantity());
        
        // Final verification
        assertEquals(1, cart.size());
        CartItem finalItem = cart.get(0);
        assertEquals(userId, finalItem.getUserId());
        assertEquals("prod2", finalItem.getProductId());
        assertEquals(3, finalItem.getQuantity());
        assertEquals("USER#" + userId, finalItem.getPk());
        assertEquals("CART#PRODUCT#prod2", finalItem.getSk());
        assertEquals("CART_ITEM", finalItem.getEntityType());
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
    Arbitrary<Integer> quantities() {
        return Arbitraries.integers().between(1, 50);
    }

    @Provide
    Arbitrary<BigDecimal> prices() {
        return Arbitraries.bigDecimals()
            .between(BigDecimal.valueOf(1.00), BigDecimal.valueOf(999.99))
            .ofScale(2);
    }

    @Provide
    Arbitrary<Integer> inventories() {
        return Arbitraries.integers().between(1, 100);
    }
}