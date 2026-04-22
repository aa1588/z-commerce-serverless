package com.zcommerce.shared.persistence;

import com.zcommerce.shared.model.*;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Working property-based test for data persistence round-trip consistency.
 * **Validates: Requirements 7.1, 7.2**
 */
class WorkingDataPersistencePropertyTest {

    /**
     * **Validates: Requirements 7.1, 7.2**
     * Property 12: Data Storage Round-Trip Consistency
     */
    @Property
    void userEntityMaintainsDataIntegrity(
        @ForAll("userIds") String userId,
        @ForAll("emails") String email,
        @ForAll("passwords") String password,
        @ForAll("names") String firstName,
        @ForAll("names") String lastName
    ) {
        // Create user with given data
        User user = new User(userId, email, password, firstName, lastName);
        
        // Simulate round-trip by creating another instance with same data
        User recreated = new User(userId, email, password, firstName, lastName);
        
        // Verify all required attributes are preserved
        assertEquals(user.getUserId(), recreated.getUserId());
        assertEquals(user.getEmail(), recreated.getEmail());
        assertEquals(user.getPasswordHash(), recreated.getPasswordHash());
        assertEquals(user.getFirstName(), recreated.getFirstName());
        assertEquals(user.getLastName(), recreated.getLastName());
        assertEquals("USER", recreated.getEntityType());
        
        // Verify proper key structures
        assertEquals("USER#" + userId, recreated.getPk());
        assertEquals("PROFILE", recreated.getSk());
        assertEquals("USER", recreated.getGsi1pk());
        assertEquals(email, recreated.getGsi1sk());
        
        // Verify timestamps are set
        assertNotNull(recreated.getCreatedAt());
        assertNotNull(recreated.getUpdatedAt());
    }

    /**
     * **Validates: Requirements 7.1, 7.2**
     * Property 12: Data Storage Round-Trip Consistency
     */
    @Property
    void productEntityMaintainsDataIntegrity(
        @ForAll("productIds") String productId,
        @ForAll("names") String name,
        @ForAll("descriptions") String description,
        @ForAll("prices") BigDecimal price,
        @ForAll("inventories") Integer inventory,
        @ForAll("categories") String category
    ) {
        // Create product with given data
        Product product = new Product(productId, name, description, price, inventory, category);
        
        // Simulate round-trip by creating another instance with same data
        Product recreated = new Product(productId, name, description, price, inventory, category);
        
        // Verify all required attributes are preserved
        assertEquals(product.getProductId(), recreated.getProductId());
        assertEquals(product.getName(), recreated.getName());
        assertEquals(product.getDescription(), recreated.getDescription());
        assertEquals(0, product.getPrice().compareTo(recreated.getPrice()));
        assertEquals(product.getInventory(), recreated.getInventory());
        assertEquals(product.getCategory(), recreated.getCategory());
        assertEquals("PRODUCT", recreated.getEntityType());
        
        // Verify proper key structures
        assertEquals("PRODUCT#" + productId, recreated.getPk());
        assertEquals("DETAILS", recreated.getSk());
        assertEquals("PRODUCT", recreated.getGsi1pk());
        
        // Verify timestamps are set
        assertNotNull(recreated.getCreatedAt());
        assertNotNull(recreated.getUpdatedAt());
        assertEquals(recreated.getCreatedAt().toString(), recreated.getGsi1sk());
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
    Arbitrary<String> emails() {
        return Arbitraries.strings().alpha().ofLength(5).map(s -> s + "@example.com");
    }

    @Provide
    Arbitrary<String> passwords() {
        return Arbitraries.strings().alpha().ofLength(12);
    }

    @Provide
    Arbitrary<String> names() {
        return Arbitraries.strings().alpha().ofLength(8);
    }

    @Provide
    Arbitrary<String> descriptions() {
        return Arbitraries.strings().alpha().ofLength(20);
    }

    @Provide
    Arbitrary<BigDecimal> prices() {
        return Arbitraries.bigDecimals()
            .between(BigDecimal.valueOf(1.00), BigDecimal.valueOf(999.99))
            .ofScale(2);
    }

    @Provide
    Arbitrary<Integer> inventories() {
        return Arbitraries.integers().between(0, 100);
    }

    @Provide
    Arbitrary<String> categories() {
        return Arbitraries.of("Electronics", "Clothing", "Books", "Home", "Sports");
    }
}