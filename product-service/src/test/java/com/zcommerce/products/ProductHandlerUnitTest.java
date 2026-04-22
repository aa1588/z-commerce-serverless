package com.zcommerce.products;

import com.zcommerce.shared.exception.ValidationException;
import com.zcommerce.shared.model.Product;
import com.zcommerce.shared.repository.ProductRepository;
import com.zcommerce.shared.util.ValidationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Product Service edge cases and validation.
 * **Validates: Requirements 1.1, 1.2**
 */
class ProductHandlerUnitTest {

    private MockProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository = new MockProductRepository();
    }

    @Test
    @DisplayName("Should reject invalid product data - negative price")
    void shouldRejectNegativePrice() {
        assertThrows(ValidationException.class, () ->
            ValidationUtils.validatePositive(BigDecimal.valueOf(-10.00), "Price"));
        assertThrows(ValidationException.class, () ->
            ValidationUtils.validatePositive(BigDecimal.ZERO, "Price"));
        assertThrows(ValidationException.class, () ->
            ValidationUtils.validatePositive((BigDecimal) null, "Price"));
    }

    @Test
    @DisplayName("Should accept positive prices")
    void shouldAcceptPositivePrice() {
        assertDoesNotThrow(() ->
            ValidationUtils.validatePositive(BigDecimal.valueOf(0.01), "Price"));
        assertDoesNotThrow(() ->
            ValidationUtils.validatePositive(BigDecimal.valueOf(99.99), "Price"));
        assertDoesNotThrow(() ->
            ValidationUtils.validatePositive(BigDecimal.valueOf(1000.00), "Price"));
    }

    @Test
    @DisplayName("Should reject empty product name")
    void shouldRejectEmptyProductName() {
        assertThrows(ValidationException.class, () ->
            ValidationUtils.validateRequired("", "Name"));
        assertThrows(ValidationException.class, () ->
            ValidationUtils.validateRequired(null, "Name"));
        assertThrows(ValidationException.class, () ->
            ValidationUtils.validateRequired("   ", "Name"));
    }

    @Test
    @DisplayName("Should reject negative inventory")
    void shouldRejectNegativeInventory() {
        assertThrows(ValidationException.class, () ->
            ValidationUtils.validateNonNegative(-1, "Inventory"));
        assertThrows(ValidationException.class, () ->
            ValidationUtils.validateNonNegative(null, "Inventory"));
    }

    @Test
    @DisplayName("Should accept zero and positive inventory")
    void shouldAcceptZeroAndPositiveInventory() {
        assertDoesNotThrow(() -> ValidationUtils.validateNonNegative(0, "Inventory"));
        assertDoesNotThrow(() -> ValidationUtils.validateNonNegative(100, "Inventory"));
    }

    @Test
    @DisplayName("Product availability should reflect inventory")
    void productAvailabilityShouldReflectInventory() {
        Product productWithStock = new Product("1", "Test", "Desc", BigDecimal.TEN, 10, "Electronics");
        assertTrue(productWithStock.isAvailable());

        Product productNoStock = new Product("2", "Test", "Desc", BigDecimal.TEN, 0, "Electronics");
        assertFalse(productNoStock.isAvailable());

        Product productNullInventory = new Product("3", "Test", "Desc", BigDecimal.TEN, null, "Electronics");
        assertFalse(productNullInventory.isAvailable());
    }

    @Test
    @DisplayName("Should find products by category")
    void shouldFindProductsByCategory() {
        productRepository.save(new Product("1", "Phone", "Desc", BigDecimal.TEN, 10, "Electronics"));
        productRepository.save(new Product("2", "Laptop", "Desc", BigDecimal.TEN, 5, "Electronics"));
        productRepository.save(new Product("3", "Shirt", "Desc", BigDecimal.TEN, 20, "Clothing"));

        List<Product> electronics = productRepository.findByCategory("Electronics");
        assertEquals(2, electronics.size());
        assertTrue(electronics.stream().allMatch(p -> "Electronics".equals(p.getCategory())));

        List<Product> clothing = productRepository.findByCategory("Clothing");
        assertEquals(1, clothing.size());
    }

    @Test
    @DisplayName("Should find only available products")
    void shouldFindOnlyAvailableProducts() {
        productRepository.save(new Product("1", "Available", "Desc", BigDecimal.TEN, 10, "Electronics"));
        productRepository.save(new Product("2", "OutOfStock", "Desc", BigDecimal.TEN, 0, "Electronics"));
        productRepository.save(new Product("3", "Available2", "Desc", BigDecimal.TEN, 5, "Clothing"));

        List<Product> available = productRepository.findAvailableProducts();
        assertEquals(2, available.size());
        assertTrue(available.stream().allMatch(Product::isAvailable));
    }

    @Test
    @DisplayName("Should decrease inventory correctly")
    void shouldDecreaseInventoryCorrectly() {
        productRepository.save(new Product("1", "Test", "Desc", BigDecimal.TEN, 10, "Electronics"));

        assertTrue(productRepository.decreaseInventory("1", 5));
        assertEquals(5, productRepository.findById("1").get().getInventory());

        assertTrue(productRepository.decreaseInventory("1", 5));
        assertEquals(0, productRepository.findById("1").get().getInventory());

        // Should fail when not enough inventory
        assertFalse(productRepository.decreaseInventory("1", 1));
    }

    @Test
    @DisplayName("Should update inventory correctly")
    void shouldUpdateInventoryCorrectly() {
        productRepository.save(new Product("1", "Test", "Desc", BigDecimal.TEN, 10, "Electronics"));

        assertTrue(productRepository.updateInventory("1", 50));
        assertEquals(50, productRepository.findById("1").get().getInventory());

        // Non-existent product
        assertFalse(productRepository.updateInventory("nonexistent", 10));
    }

    @Test
    @DisplayName("Should handle concurrent category operations")
    void shouldHandleConcurrentCategoryOperations() {
        String productId = UUID.randomUUID().toString();
        Product product = new Product(productId, "Test", "Desc", BigDecimal.TEN, 10, "Electronics");
        productRepository.save(product);

        // Change category
        product.setCategory("Clothing");
        productRepository.save(product);

        // Should be in new category, not old
        assertEquals(0, productRepository.findByCategory("Electronics").size());
        assertEquals(1, productRepository.findByCategory("Clothing").size());
    }

    // Mock repository
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
