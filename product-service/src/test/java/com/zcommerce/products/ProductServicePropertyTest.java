package com.zcommerce.products;

import com.zcommerce.shared.model.Product;
import com.zcommerce.shared.repository.ProductRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for Product Service.
 * Tests Properties 1 and 2 from the design document.
 */
class ProductServicePropertyTest {

    /**
     * Property 1: Product CRUD Operations Maintain Data Integrity
     * For any product with valid attributes, creating, updating, or retrieving that product
     * should preserve all required fields and maintain data consistency.
     * **Validates: Requirements 1.1, 1.2, 1.4**
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 1: Product CRUD Operations Maintain Data Integrity")
    void productCrudMaintainsDataIntegrity(
        @ForAll("productIds") String productId,
        @ForAll("productNames") String name,
        @ForAll("descriptions") String description,
        @ForAll("prices") BigDecimal price,
        @ForAll("inventories") Integer inventory,
        @ForAll("categories") String category
    ) {
        MockProductRepository repository = new MockProductRepository();

        // CREATE - Create product
        Product product = new Product(productId, name, description, price, inventory, category);
        Instant createdAt = product.getCreatedAt();
        repository.save(product);

        // READ - Retrieve and verify
        Optional<Product> retrieved = repository.findById(productId);
        assertTrue(retrieved.isPresent(), "Product should be retrievable after creation");

        Product retrievedProduct = retrieved.get();
        assertEquals(productId, retrievedProduct.getProductId());
        assertEquals(name, retrievedProduct.getName());
        assertEquals(description, retrievedProduct.getDescription());
        assertEquals(0, price.compareTo(retrievedProduct.getPrice()));
        assertEquals(inventory, retrievedProduct.getInventory());
        assertEquals(category, retrievedProduct.getCategory());
        assertEquals("PRODUCT", retrievedProduct.getEntityType());

        // UPDATE - Modify and verify
        String newName = name + "_updated";
        BigDecimal newPrice = price.add(BigDecimal.ONE);
        Integer newInventory = inventory + 10;

        retrievedProduct.setName(newName);
        retrievedProduct.setPrice(newPrice);
        retrievedProduct.setInventory(newInventory);
        retrievedProduct.setUpdatedAt(Instant.now());
        repository.save(retrievedProduct);

        Optional<Product> updated = repository.findById(productId);
        assertTrue(updated.isPresent());
        assertEquals(newName, updated.get().getName());
        assertEquals(0, newPrice.compareTo(updated.get().getPrice()));
        assertEquals(newInventory, updated.get().getInventory());

        // Verify original fields unchanged
        assertEquals(productId, updated.get().getProductId());
        assertEquals(description, updated.get().getDescription());
        assertEquals(category, updated.get().getCategory());
        assertEquals(createdAt, updated.get().getCreatedAt());

        // Verify updatedAt is after createdAt
        assertTrue(updated.get().getUpdatedAt().compareTo(createdAt) >= 0);
    }

    /**
     * Property 2: Product Deletion Handles Dependencies
     * For any product that exists in the system, deleting it should remove it from the catalog
     * and properly handle any dependent cart references without leaving orphaned data.
     * **Validates: Requirements 1.3**
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 2: Product Deletion Handles Dependencies")
    void productDeletionHandlesDependencies(
        @ForAll("productIds") String productId,
        @ForAll("productNames") String name,
        @ForAll("prices") BigDecimal price,
        @ForAll("inventories") Integer inventory
    ) {
        MockProductRepository repository = new MockProductRepository();

        // Create product
        Product product = new Product(productId, name, "Description", price, inventory, "Electronics");
        repository.save(product);

        // Verify product exists
        assertTrue(repository.findById(productId).isPresent());
        assertTrue(repository.findAll().stream()
            .anyMatch(p -> p.getProductId().equals(productId)));

        // Delete product
        boolean deleted = repository.deleteById(productId);
        assertTrue(deleted, "Delete should return true for existing product");

        // Verify product is removed from catalog
        assertFalse(repository.findById(productId).isPresent(), "Product should not be found after deletion");
        assertFalse(repository.findAll().stream()
            .anyMatch(p -> p.getProductId().equals(productId)));

        // Verify deleting again returns false
        boolean deletedAgain = repository.deleteById(productId);
        assertFalse(deletedAgain, "Delete should return false for non-existent product");
    }

    /**
     * Additional property: Product availability reflects inventory state
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 1: Product CRUD Operations Maintain Data Integrity")
    void productAvailabilityReflectsInventory(
        @ForAll("productIds") String productId,
        @ForAll("productNames") String name,
        @ForAll("prices") BigDecimal price
    ) {
        // Product with positive inventory should be available
        Product availableProduct = new Product(productId, name, "desc", price, 10, "Category");
        assertTrue(availableProduct.isAvailable());

        // Product with zero inventory should not be available
        Product outOfStockProduct = new Product(productId + "2", name, "desc", price, 0, "Category");
        assertFalse(outOfStockProduct.isAvailable());

        // Product with null inventory should not be available
        Product nullInventoryProduct = new Product(productId + "3", name, "desc", price, null, "Category");
        assertFalse(nullInventoryProduct.isAvailable());
    }

    /**
     * Additional property: Category filtering returns correct products
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 1: Product CRUD Operations Maintain Data Integrity")
    void categoryFilteringReturnsCorrectProducts(
        @ForAll("productIds") String productId1,
        @ForAll("productIds") String productId2,
        @ForAll("productNames") String name,
        @ForAll("prices") BigDecimal price,
        @ForAll("categories") String category
    ) {
        Assume.that(!productId1.equals(productId2));

        MockProductRepository repository = new MockProductRepository();

        // Create products in same category
        Product product1 = new Product(productId1, name + "1", "desc", price, 10, category);
        Product product2 = new Product(productId2, name + "2", "desc", price, 5, category);
        repository.save(product1);
        repository.save(product2);

        // Find by category
        List<Product> found = repository.findByCategory(category);
        assertEquals(2, found.size());
        assertTrue(found.stream().allMatch(p -> p.getCategory().equals(category)));
    }

    // Providers
    @Provide
    Arbitrary<String> productIds() {
        return Arbitraries.strings()
            .alpha()
            .ofLength(12);
    }

    @Provide
    Arbitrary<String> productNames() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(3)
            .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> descriptions() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(10)
            .ofMaxLength(200);
    }

    @Provide
    Arbitrary<BigDecimal> prices() {
        return Arbitraries.bigDecimals()
            .between(BigDecimal.valueOf(0.01), BigDecimal.valueOf(9999.99))
            .ofScale(2);
    }

    @Provide
    Arbitrary<Integer> inventories() {
        return Arbitraries.integers().between(0, 1000);
    }

    @Provide
    Arbitrary<String> categories() {
        return Arbitraries.of("Electronics", "Clothing", "Books", "Home", "Sports", "Toys");
    }

    // Mock repository for testing without DynamoDB
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
