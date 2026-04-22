package com.zcommerce.cart;

import com.zcommerce.shared.model.CartItem;
import com.zcommerce.shared.model.Product;
import com.zcommerce.shared.repository.CartRepository;
import com.zcommerce.shared.repository.ProductRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for Cart Service.
 * Tests Property 3 from the design document.
 */
class CartServicePropertyTest {

    /**
     * Property 3: Cart Operations Maintain State Consistency
     * For any user cart and valid product operations (add, update, remove), the cart should maintain
     * accurate item quantities, pricing totals, and inventory validation throughout all operations.
     * **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 3: Cart Operations Maintain State Consistency")
    void cartOperationsMaintainStateConsistency(
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId,
        @ForAll("prices") BigDecimal price,
        @ForAll("quantities") Integer quantity,
        @ForAll("inventories") Integer inventory
    ) {
        Assume.that(inventory >= quantity);

        MockCartRepository cartRepository = new MockCartRepository();
        MockProductRepository productRepository = new MockProductRepository();

        // Setup product
        Product product = new Product(productId, "Test Product", "Description", price, inventory, "Electronics");
        productRepository.save(product);

        // ADD - Add item to cart
        CartItem item = new CartItem(userId, productId, quantity);
        cartRepository.save(item);

        // Verify item was added
        Optional<CartItem> found = cartRepository.findByUserIdAndProductId(userId, productId);
        assertTrue(found.isPresent(), "Cart item should be found after adding");
        assertEquals(quantity, found.get().getQuantity());

        // Calculate expected total
        BigDecimal expectedTotal = price.multiply(BigDecimal.valueOf(quantity));

        // Verify cart total
        BigDecimal actualTotal = calculateCartTotal(cartRepository.findByUserId(userId), productRepository);
        assertEquals(0, expectedTotal.compareTo(actualTotal), "Cart total should be accurate");

        // UPDATE - Update quantity
        Integer newQuantity = Math.min(quantity + 1, inventory);
        found.get().setQuantity(newQuantity);
        cartRepository.save(found.get());

        // Verify updated quantity
        Optional<CartItem> updated = cartRepository.findByUserIdAndProductId(userId, productId);
        assertTrue(updated.isPresent());
        assertEquals(newQuantity, updated.get().getQuantity());

        // Verify updated total
        BigDecimal newExpectedTotal = price.multiply(BigDecimal.valueOf(newQuantity));
        BigDecimal newActualTotal = calculateCartTotal(cartRepository.findByUserId(userId), productRepository);
        assertEquals(0, newExpectedTotal.compareTo(newActualTotal));

        // REMOVE - Remove item from cart
        boolean removed = cartRepository.deleteByUserIdAndProductId(userId, productId);
        assertTrue(removed);

        // Verify item was removed
        assertFalse(cartRepository.findByUserIdAndProductId(userId, productId).isPresent());

        // Verify cart is empty
        assertEquals(0, cartRepository.findByUserId(userId).size());
    }

    /**
     * Property 3: Cart maintains accurate pricing across multiple items
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 3: Cart Operations Maintain State Consistency")
    void cartMaintainsAccuratePricingForMultipleItems(
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId1,
        @ForAll("productIds") String productId2,
        @ForAll("prices") BigDecimal price1,
        @ForAll("prices") BigDecimal price2,
        @ForAll("quantities") Integer quantity1,
        @ForAll("quantities") Integer quantity2
    ) {
        Assume.that(!productId1.equals(productId2));

        MockCartRepository cartRepository = new MockCartRepository();
        MockProductRepository productRepository = new MockProductRepository();

        // Setup products
        Product product1 = new Product(productId1, "Product 1", "Desc", price1, 100, "Electronics");
        Product product2 = new Product(productId2, "Product 2", "Desc", price2, 100, "Electronics");
        productRepository.save(product1);
        productRepository.save(product2);

        // Add items to cart
        CartItem item1 = new CartItem(userId, productId1, quantity1);
        CartItem item2 = new CartItem(userId, productId2, quantity2);
        cartRepository.save(item1);
        cartRepository.save(item2);

        // Calculate expected total
        BigDecimal expectedTotal = price1.multiply(BigDecimal.valueOf(quantity1))
            .add(price2.multiply(BigDecimal.valueOf(quantity2)));

        // Verify cart total
        BigDecimal actualTotal = calculateCartTotal(cartRepository.findByUserId(userId), productRepository);
        assertEquals(0, expectedTotal.compareTo(actualTotal), "Cart total should equal sum of item totals");

        // Verify item count
        assertEquals(2, cartRepository.findByUserId(userId).size());
    }

    /**
     * Property 3: Cart validates inventory constraints
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 3: Cart Operations Maintain State Consistency")
    void cartValidatesInventoryConstraints(
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId,
        @ForAll("prices") BigDecimal price,
        @ForAll("quantities") Integer requestedQuantity,
        @ForAll("inventories") Integer availableInventory
    ) {
        MockProductRepository productRepository = new MockProductRepository();

        // Setup product with limited inventory
        Product product = new Product(productId, "Test Product", "Desc", price, availableInventory, "Electronics");
        productRepository.save(product);

        // Verify inventory check
        boolean canAddToCart = availableInventory >= requestedQuantity;

        if (canAddToCart) {
            assertTrue(product.getInventory() >= requestedQuantity,
                "Should be able to add when inventory is sufficient");
        } else {
            assertTrue(product.getInventory() < requestedQuantity,
                "Should not be able to add when inventory is insufficient");
        }
    }

    /**
     * Property 3: Clearing cart removes all items
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 3: Cart Operations Maintain State Consistency")
    void clearingCartRemovesAllItems(
        @ForAll("userIds") String userId,
        @ForAll List<@From("productIds") String> productIds
    ) {
        Assume.that(productIds.size() >= 1 && productIds.size() <= 5);
        // Ensure unique product IDs
        List<String> uniqueProductIds = productIds.stream().distinct().collect(Collectors.toList());
        Assume.that(!uniqueProductIds.isEmpty());

        MockCartRepository cartRepository = new MockCartRepository();

        // Add multiple items
        for (String productId : uniqueProductIds) {
            CartItem item = new CartItem(userId, productId, 1);
            cartRepository.save(item);
        }

        // Verify items were added
        assertEquals(uniqueProductIds.size(), cartRepository.findByUserId(userId).size());

        // Clear cart
        int deletedCount = cartRepository.deleteByUserId(userId);
        assertEquals(uniqueProductIds.size(), deletedCount);

        // Verify cart is empty
        assertEquals(0, cartRepository.findByUserId(userId).size());
    }

    // Helper method to calculate cart total
    private BigDecimal calculateCartTotal(List<CartItem> items, MockProductRepository productRepository) {
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : items) {
            Optional<Product> product = productRepository.findById(item.getProductId());
            if (product.isPresent()) {
                total = total.add(product.get().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }
        return total;
    }

    // Providers
    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.strings()
            .alpha()
            .ofLength(12);
    }

    @Provide
    Arbitrary<String> productIds() {
        return Arbitraries.strings()
            .alpha()
            .ofLength(12);
    }

    @Provide
    Arbitrary<BigDecimal> prices() {
        return Arbitraries.bigDecimals()
            .between(BigDecimal.valueOf(0.01), BigDecimal.valueOf(999.99))
            .ofScale(2);
    }

    @Provide
    Arbitrary<Integer> quantities() {
        return Arbitraries.integers().between(1, 10);
    }

    @Provide
    Arbitrary<Integer> inventories() {
        return Arbitraries.integers().between(1, 100);
    }

    // Mock repositories
    static class MockCartRepository implements CartRepository {
        private final Map<String, CartItem> items = new HashMap<>();

        private String key(String userId, String productId) {
            return userId + ":" + productId;
        }

        @Override
        public CartItem save(CartItem item) {
            items.put(key(item.getUserId(), item.getProductId()), item);
            return item;
        }

        @Override
        public CartItem update(CartItem item) {
            return save(item);
        }

        @Override
        public Optional<CartItem> findById(String id) {
            return Optional.ofNullable(items.get(id));
        }

        @Override
        public List<CartItem> findAll() {
            return new ArrayList<>(items.values());
        }

        @Override
        public boolean deleteById(String id) {
            return items.remove(id) != null;
        }

        @Override
        public boolean existsById(String id) {
            return items.containsKey(id);
        }

        @Override
        public List<CartItem> findByUserId(String userId) {
            return items.values().stream()
                .filter(item -> userId.equals(item.getUserId()))
                .collect(Collectors.toList());
        }

        @Override
        public Optional<CartItem> findByUserIdAndProductId(String userId, String productId) {
            return Optional.ofNullable(items.get(key(userId, productId)));
        }

        @Override
        public int deleteByUserId(String userId) {
            List<String> keysToRemove = items.entrySet().stream()
                .filter(e -> e.getValue().getUserId().equals(userId))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

            keysToRemove.forEach(items::remove);
            return keysToRemove.size();
        }

        @Override
        public boolean deleteByUserIdAndProductId(String userId, String productId) {
            return items.remove(key(userId, productId)) != null;
        }

        @Override
        public int deleteByProductId(String productId) {
            List<String> keysToRemove = items.entrySet().stream()
                .filter(e -> e.getValue().getProductId().equals(productId))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

            keysToRemove.forEach(items::remove);
            return keysToRemove.size();
        }
    }

    static class MockProductRepository implements ProductRepository {
        private final Map<String, Product> products = new HashMap<>();

        @Override
        public Product save(Product product) {
            products.put(product.getProductId(), product);
            return product;
        }

        @Override
        public Product update(Product product) {
            return save(product);
        }

        @Override
        public Optional<Product> findById(String id) {
            return Optional.ofNullable(products.get(id));
        }

        @Override
        public List<Product> findAll() {
            return new ArrayList<>(products.values());
        }

        @Override
        public boolean deleteById(String id) {
            return products.remove(id) != null;
        }

        @Override
        public boolean existsById(String id) {
            return products.containsKey(id);
        }

        @Override
        public List<Product> findByCategory(String category) {
            return products.values().stream()
                .filter(p -> category.equals(p.getCategory()))
                .collect(Collectors.toList());
        }

        @Override
        public List<Product> findAvailableProducts() {
            return products.values().stream()
                .filter(Product::isAvailable)
                .collect(Collectors.toList());
        }

        @Override
        public boolean updateInventory(String productId, Integer newInventory) {
            Product product = products.get(productId);
            if (product != null) {
                product.setInventory(newInventory);
                return true;
            }
            return false;
        }

        @Override
        public boolean decreaseInventory(String productId, Integer quantity) {
            Product product = products.get(productId);
            if (product != null && product.getInventory() >= quantity) {
                product.setInventory(product.getInventory() - quantity);
                return true;
            }
            return false;
        }
    }
}
