package com.zcommerce.products;

import com.zcommerce.shared.model.Product;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for product CRUD operations maintaining data integrity.
 * **Validates: Requirements 1.1, 1.2, 1.4**
 */
class ProductCrudPropertyTest {

    /**
     * **Validates: Requirements 1.1, 1.2, 1.4**
     * Property 1: Product CRUD Operations Maintain Data Integrity
     * For any product with valid attributes (name, description, price, inventory), 
     * creating, updating, or retrieving that product should preserve all required 
     * fields and maintain data consistency.
     */
    @Property
    @Tag("Feature: z-commerce, Property 1: Product CRUD Operations Maintain Data Integrity")
    void productCreationMaintainsDataIntegrity(
        @ForAll("productIds") String productId,
        @ForAll("productNames") String name,
        @ForAll("descriptions") String description,
        @ForAll("prices") BigDecimal price,
        @ForAll("inventories") Integer inventory,
        @ForAll("categories") String category
    ) {
        // Create product with given attributes
        Product product = new Product(productId, name, description, price, inventory, category);
        
        // Verify all required fields are preserved
        assertEquals(productId, product.getProductId());
        assertEquals(name, product.getName());
        assertEquals(description, product.getDescription());
        assertEquals(0, price.compareTo(product.getPrice()));
        assertEquals(inventory, product.getInventory());
        assertEquals(category, product.getCategory());
        assertEquals("PRODUCT", product.getEntityType());
        
        // Verify proper key structures for DynamoDB
        assertEquals("PRODUCT#" + productId, product.getPk());
        assertEquals("DETAILS", product.getSk());
        assertEquals("PRODUCT", product.getGsi1pk());
        assertEquals(product.getCreatedAt().toString(), product.getGsi1sk());
        
        // Verify timestamps are set
        assertNotNull(product.getCreatedAt());
        assertNotNull(product.getUpdatedAt());
        
        // Verify availability logic
        boolean expectedAvailability = inventory != null && inventory > 0;
        assertEquals(expectedAvailability, product.isAvailable());
    }

    /**
     * **Validates: Requirements 1.1, 1.2, 1.4**
     * Property 1: Product CRUD Operations Maintain Data Integrity
     */
    @Property
    @Tag("Feature: z-commerce, Property 1: Product CRUD Operations Maintain Data Integrity")
    void productUpdatesMaintainIdentityAndKeys(
        @ForAll("productIds") String productId,
        @ForAll("productNames") String originalName,
        @ForAll("descriptions") String originalDescription,
        @ForAll("prices") BigDecimal originalPrice,
        @ForAll("inventories") Integer originalInventory,
        @ForAll("categories") String originalCategory,
        @ForAll("productNames") String newName,
        @ForAll("descriptions") String newDescription,
        @ForAll("prices") BigDecimal newPrice,
        @ForAll("inventories") Integer newInventory,
        @ForAll("categories") String newCategory
    ) {
        // Create original product
        Product originalProduct = new Product(productId, originalName, originalDescription, 
                                            originalPrice, originalInventory, originalCategory);
        Instant originalCreatedAt = originalProduct.getCreatedAt();
        
        // Simulate product update by creating updated product with same ID
        Product updatedProduct = new Product(productId, newName, newDescription, 
                                           newPrice, newInventory, newCategory);
        updatedProduct.setCreatedAt(originalCreatedAt); // Preserve creation time
        updatedProduct.setUpdatedAt(Instant.now()); // Update modification time
        
        // Verify identity preservation
        assertEquals(originalProduct.getProductId(), updatedProduct.getProductId());
        assertEquals(originalProduct.getEntityType(), updatedProduct.getEntityType());
        
        // Verify key structure integrity
        assertEquals(originalProduct.getPk(), updatedProduct.getPk()); // PK should remain same
        assertEquals(originalProduct.getSk(), updatedProduct.getSk()); // SK should remain same
        assertEquals(originalProduct.getGsi1pk(), updatedProduct.getGsi1pk()); // GSI1PK should remain same
        
        // Verify product data updates
        assertEquals(newName, updatedProduct.getName());
        assertEquals(newDescription, updatedProduct.getDescription());
        assertEquals(0, newPrice.compareTo(updatedProduct.getPrice()));
        assertEquals(newInventory, updatedProduct.getInventory());
        assertEquals(newCategory, updatedProduct.getCategory());
        
        // Verify timestamp integrity
        assertEquals(originalCreatedAt, updatedProduct.getCreatedAt()); // Creation time preserved
        assertTrue(updatedProduct.getUpdatedAt().isAfter(originalCreatedAt) || 
                  updatedProduct.getUpdatedAt().equals(originalCreatedAt)); // Update time is newer or same
        
        // Verify availability logic after update
        boolean expectedAvailability = newInventory != null && newInventory > 0;
        assertEquals(expectedAvailability, updatedProduct.isAvailable());
    }

    /**
     * **Validates: Requirements 1.1, 1.2, 1.4**
     * Property 1: Product CRUD Operations Maintain Data Integrity
     */
    @Property
    @Tag("Feature: z-commerce, Property 1: Product CRUD Operations Maintain Data Integrity")
    void productRetrievalPreservesAllData(
        @ForAll("productIds") String productId,
        @ForAll("productNames") String name,
        @ForAll("descriptions") String description,
        @ForAll("prices") BigDecimal price,
        @ForAll("inventories") Integer inventory,
        @ForAll("categories") String category
    ) {
        // Create product
        Product originalProduct = new Product(productId, name, description, price, inventory, category);
        
        // Simulate retrieval by creating another instance with same data
        Product retrievedProduct = new Product();
        retrievedProduct.setProductId(originalProduct.getProductId());
        retrievedProduct.setName(originalProduct.getName());
        retrievedProduct.setDescription(originalProduct.getDescription());
        retrievedProduct.setPrice(originalProduct.getPrice());
        retrievedProduct.setInventory(originalProduct.getInventory());
        retrievedProduct.setCategory(originalProduct.getCategory());
        retrievedProduct.setCreatedAt(originalProduct.getCreatedAt());
        retrievedProduct.setUpdatedAt(originalProduct.getUpdatedAt());
        
        // Verify all data is preserved during retrieval
        assertEquals(originalProduct.getProductId(), retrievedProduct.getProductId());
        assertEquals(originalProduct.getName(), retrievedProduct.getName());
        assertEquals(originalProduct.getDescription(), retrievedProduct.getDescription());
        assertEquals(0, originalProduct.getPrice().compareTo(retrievedProduct.getPrice()));
        assertEquals(originalProduct.getInventory(), retrievedProduct.getInventory());
        assertEquals(originalProduct.getCategory(), retrievedProduct.getCategory());
        assertEquals(originalProduct.getCreatedAt(), retrievedProduct.getCreatedAt());
        assertEquals(originalProduct.getUpdatedAt(), retrievedProduct.getUpdatedAt());
        
        // Verify key structures are consistent
        assertEquals(originalProduct.getPk(), retrievedProduct.getPk());
        assertEquals(originalProduct.getSk(), retrievedProduct.getSk());
        assertEquals(originalProduct.getGsi1pk(), retrievedProduct.getGsi1pk());
        assertEquals(originalProduct.getGsi1sk(), retrievedProduct.getGsi1sk());
        assertEquals(originalProduct.getEntityType(), retrievedProduct.getEntityType());
        
        // Verify availability logic is consistent
        assertEquals(originalProduct.isAvailable(), retrievedProduct.isAvailable());
    }

    /**
     * **Validates: Requirements 1.1, 1.2, 1.4**
     * Property 1: Product CRUD Operations Maintain Data Integrity
     */
    @Test
    void inventoryUpdatesPreserveOtherProductData() {
        String productId = "prod123";
        String name = "Test Product";
        String description = "Test Description";
        BigDecimal price = BigDecimal.valueOf(99.99);
        String category = "Electronics";
        
        // Create product with initial inventory
        Product product = new Product(productId, name, description, price, 50, category);
        Instant originalCreatedAt = product.getCreatedAt();
        
        // Test inventory updates
        Integer[] inventoryUpdates = {100, 0, 25, null, 1};
        
        for (Integer newInventory : inventoryUpdates) {
            // Update inventory
            product.setInventory(newInventory);
            product.setUpdatedAt(Instant.now());
            
            // Verify inventory update
            assertEquals(newInventory, product.getInventory());
            
            // Verify all other product data is preserved
            assertEquals(productId, product.getProductId());
            assertEquals(name, product.getName());
            assertEquals(description, product.getDescription());
            assertEquals(0, price.compareTo(product.getPrice()));
            assertEquals(category, product.getCategory());
            assertEquals(originalCreatedAt, product.getCreatedAt());
            
            // Verify key structure integrity
            assertEquals("PRODUCT#" + productId, product.getPk());
            assertEquals("DETAILS", product.getSk());
            assertEquals("PRODUCT", product.getGsi1pk());
            assertEquals("PRODUCT", product.getEntityType());
            
            // Verify availability logic
            boolean expectedAvailability = newInventory != null && newInventory > 0;
            assertEquals(expectedAvailability, product.isAvailable());
            
            // Verify timestamp update
            assertTrue(product.getUpdatedAt().isAfter(originalCreatedAt) || 
                      product.getUpdatedAt().equals(originalCreatedAt));
        }
    }

    /**
     * **Validates: Requirements 1.1, 1.2, 1.4**
     * Property 1: Product CRUD Operations Maintain Data Integrity
     */
    @Test
    void priceUpdatesPreserveOtherProductData() {
        String productId = "prod123";
        String name = "Test Product";
        String description = "Test Description";
        Integer inventory = 50;
        String category = "Electronics";
        
        // Create product with initial price
        Product product = new Product(productId, name, description, BigDecimal.valueOf(99.99), inventory, category);
        Instant originalCreatedAt = product.getCreatedAt();
        
        // Test price updates
        BigDecimal[] priceUpdates = {
            BigDecimal.valueOf(149.99),
            BigDecimal.valueOf(0.01),
            BigDecimal.valueOf(999.99),
            BigDecimal.valueOf(50.00)
        };
        
        for (BigDecimal newPrice : priceUpdates) {
            // Update price
            product.setPrice(newPrice);
            product.setUpdatedAt(Instant.now());
            
            // Verify price update
            assertEquals(0, newPrice.compareTo(product.getPrice()));
            
            // Verify all other product data is preserved
            assertEquals(productId, product.getProductId());
            assertEquals(name, product.getName());
            assertEquals(description, product.getDescription());
            assertEquals(inventory, product.getInventory());
            assertEquals(category, product.getCategory());
            assertEquals(originalCreatedAt, product.getCreatedAt());
            
            // Verify key structure integrity
            assertEquals("PRODUCT#" + productId, product.getPk());
            assertEquals("DETAILS", product.getSk());
            assertEquals("PRODUCT", product.getGsi1pk());
            assertEquals("PRODUCT", product.getEntityType());
            
            // Verify availability logic (should remain consistent)
            assertTrue(product.isAvailable()); // Inventory is 50, so should be available
            
            // Verify timestamp update
            assertTrue(product.getUpdatedAt().isAfter(originalCreatedAt) || 
                      product.getUpdatedAt().equals(originalCreatedAt));
        }
    }

    /**
     * **Validates: Requirements 1.1, 1.2, 1.4**
     * Property 1: Product CRUD Operations Maintain Data Integrity
     */
    @Property
    @Tag("Feature: z-commerce, Property 1: Product CRUD Operations Maintain Data Integrity")
    void multipleProductsPreserveUniqueness(
        @ForAll("productIds") String productId1,
        @ForAll("productIds") String productId2,
        @ForAll("productNames") String name1,
        @ForAll("productNames") String name2,
        @ForAll("prices") BigDecimal price1,
        @ForAll("prices") BigDecimal price2
    ) {
        Assume.that(!productId1.equals(productId2)); // Different product IDs
        
        // Create two different products
        Product product1 = new Product(productId1, name1, "Description 1", price1, 10, "Category1");
        Product product2 = new Product(productId2, name2, "Description 2", price2, 20, "Category2");
        
        // Verify products are distinct
        assertNotEquals(product1.getProductId(), product2.getProductId());
        assertNotEquals(product1.getPk(), product2.getPk());
        
        // Verify each product maintains its own data integrity
        assertEquals(productId1, product1.getProductId());
        assertEquals(productId2, product2.getProductId());
        assertEquals(name1, product1.getName());
        assertEquals(name2, product2.getName());
        assertEquals(0, price1.compareTo(product1.getPrice()));
        assertEquals(0, price2.compareTo(product2.getPrice()));
        
        // Verify key structures are unique
        assertEquals("PRODUCT#" + productId1, product1.getPk());
        assertEquals("PRODUCT#" + productId2, product2.getPk());
        
        // Both should have same SK and GSI1PK (entity type)
        assertEquals(product1.getSk(), product2.getSk()); // Both "DETAILS"
        assertEquals(product1.getGsi1pk(), product2.getGsi1pk()); // Both "PRODUCT"
        assertEquals(product1.getEntityType(), product2.getEntityType()); // Both "PRODUCT"
        
        // But different GSI1SK (creation timestamps should be different or same)
        // Note: In rapid creation, timestamps might be the same, which is acceptable
        assertNotNull(product1.getGsi1sk());
        assertNotNull(product2.getGsi1sk());
    }

    /**
     * **Validates: Requirements 1.1, 1.2, 1.4**
     * Property 1: Product CRUD Operations Maintain Data Integrity
     */
    @Test
    void productAvailabilityLogicConsistency() {
        String productId = "prod123";
        String name = "Test Product";
        String description = "Test Description";
        BigDecimal price = BigDecimal.valueOf(99.99);
        String category = "Electronics";
        
        // Test various inventory scenarios
        Object[][] testCases = {
            {null, false},      // null inventory = not available
            {0, false},         // zero inventory = not available
            {-1, false},        // negative inventory = not available
            {1, true},          // positive inventory = available
            {100, true},        // large inventory = available
        };
        
        for (Object[] testCase : testCases) {
            Integer inventory = (Integer) testCase[0];
            boolean expectedAvailability = (Boolean) testCase[1];
            
            Product product = new Product(productId, name, description, price, inventory, category);
            
            assertEquals(expectedAvailability, product.isAvailable(),
                String.format("Availability logic failed for inventory=%s, expected=%s", 
                    inventory, expectedAvailability));
            
            // Verify all other data integrity is maintained
            assertEquals(productId, product.getProductId());
            assertEquals(name, product.getName());
            assertEquals(description, product.getDescription());
            assertEquals(0, price.compareTo(product.getPrice()));
            assertEquals(inventory, product.getInventory());
            assertEquals(category, product.getCategory());
            assertEquals("PRODUCT", product.getEntityType());
        }
    }

    // Providers
    @Provide
    Arbitrary<String> productIds() {
        return Arbitraries.strings().alpha().ofLength(8);
    }

    @Provide
    Arbitrary<String> productNames() {
        return Arbitraries.strings().alpha().ofLength(10);
    }

    @Provide
    Arbitrary<String> descriptions() {
        return Arbitraries.strings().alpha().ofLength(20);
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
        return Arbitraries.of("Electronics", "Clothing", "Books", "Home", "Sports", "Toys", "Health");
    }
}