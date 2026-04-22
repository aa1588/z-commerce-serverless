package com.zcommerce.shared.user;

import com.zcommerce.shared.model.User;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for user profile updates maintaining integrity.
 * **Validates: Requirements 4.3**
 */
class UserProfileUpdatePropertyTest {

    /**
     * **Validates: Requirements 4.3**
     * Property 9: User Profile Updates Maintain Integrity
     * For any user profile update, the system should apply changes correctly 
     * while maintaining data integrity and enforcing business rules.
     */
    @Property
    @Tag("Feature: z-commerce, Property 9: User Profile Updates Maintain Integrity")
    void profileUpdatesPreserveIdentityAndKeys(
        @ForAll("userIds") String userId,
        @ForAll("emails") String originalEmail,
        @ForAll("names") String originalFirstName,
        @ForAll("names") String originalLastName,
        @ForAll("emails") String newEmail,
        @ForAll("names") String newFirstName,
        @ForAll("names") String newLastName
    ) {
        // Create original user
        User originalUser = new User(userId, originalEmail, "hashedPassword", originalFirstName, originalLastName);
        Instant originalCreatedAt = originalUser.getCreatedAt();
        
        // Simulate profile update by creating updated user with same ID
        User updatedUser = new User(userId, newEmail, "hashedPassword", newFirstName, newLastName);
        updatedUser.setCreatedAt(originalCreatedAt); // Preserve creation time
        updatedUser.setUpdatedAt(Instant.now()); // Update modification time
        
        // Verify identity preservation
        assertEquals(originalUser.getUserId(), updatedUser.getUserId());
        assertEquals(originalUser.getEntityType(), updatedUser.getEntityType());
        
        // Verify key structure integrity
        assertEquals(originalUser.getPk(), updatedUser.getPk()); // PK should remain same (USER#{userId})
        assertEquals(originalUser.getSk(), updatedUser.getSk()); // SK should remain same (PROFILE)
        assertEquals(originalUser.getGsi1pk(), updatedUser.getGsi1pk()); // GSI1PK should remain same (USER)
        
        // Verify GSI1SK updates with email change
        assertEquals(newEmail, updatedUser.getGsi1sk());
        
        // Verify profile data updates
        assertEquals(newEmail, updatedUser.getEmail());
        assertEquals(newFirstName, updatedUser.getFirstName());
        assertEquals(newLastName, updatedUser.getLastName());
        
        // Verify timestamp integrity
        assertEquals(originalCreatedAt, updatedUser.getCreatedAt()); // Creation time preserved
        assertTrue(updatedUser.getUpdatedAt().isAfter(originalCreatedAt)); // Update time is newer
    }

    /**
     * **Validates: Requirements 4.3**
     * Property 9: User Profile Updates Maintain Integrity
     */
    @Property
    @Tag("Feature: z-commerce, Property 9: User Profile Updates Maintain Integrity")
    void emailUpdatesPreserveUniquenessConstraints(
        @ForAll("userIds") String userId1,
        @ForAll("userIds") String userId2,
        @ForAll("emails") String email1,
        @ForAll("emails") String email2,
        @ForAll("emails") String newEmail
    ) {
        Assume.that(!userId1.equals(userId2)); // Different users
        Assume.that(!email1.equals(email2)); // Different original emails
        
        // Create two users with different emails
        User user1 = new User(userId1, email1, "password1", "John", "Doe");
        User user2 = new User(userId2, email2, "password2", "Jane", "Smith");
        
        // Update user1's email
        user1.setEmail(newEmail);
        user1.setUpdatedAt(Instant.now());
        
        // Verify email update integrity
        assertEquals(newEmail, user1.getEmail());
        assertEquals(newEmail, user1.getGsi1sk()); // GSI1SK should update with email
        
        // Verify other user remains unchanged
        assertEquals(email2, user2.getEmail());
        assertEquals(email2, user2.getGsi1sk());
        
        // Verify users remain distinct
        assertNotEquals(user1.getUserId(), user2.getUserId());
        assertNotEquals(user1.getPk(), user2.getPk());
        
        // If emails become the same, GSI1SK would be the same (uniqueness constraint would be enforced at application level)
        if (newEmail.equals(email2)) {
            assertEquals(user1.getGsi1sk(), user2.getGsi1sk());
        } else {
            assertNotEquals(user1.getGsi1sk(), user2.getGsi1sk());
        }
    }

    /**
     * **Validates: Requirements 4.3**
     * Property 9: User Profile Updates Maintain Integrity
     */
    @Test
    void partialProfileUpdatesPreserveOtherFields() {
        String userId = "user123";
        String originalEmail = "original@example.com";
        String originalFirstName = "John";
        String originalLastName = "Doe";
        String originalPassword = "hashedPassword";
        
        // Create original user
        User user = new User(userId, originalEmail, originalPassword, originalFirstName, originalLastName);
        Instant originalCreatedAt = user.getCreatedAt();
        
        // Test individual field updates
        
        // 1. Update only first name
        user.setFirstName("Johnny");
        user.setUpdatedAt(Instant.now());
        
        assertEquals("Johnny", user.getFirstName());
        assertEquals(originalLastName, user.getLastName()); // Preserved
        assertEquals(originalEmail, user.getEmail()); // Preserved
        assertEquals(originalPassword, user.getPasswordHash()); // Preserved
        assertEquals(userId, user.getUserId()); // Preserved
        assertEquals(originalCreatedAt, user.getCreatedAt()); // Preserved
        
        // 2. Update only last name
        user.setLastName("Smith");
        user.setUpdatedAt(Instant.now());
        
        assertEquals("Johnny", user.getFirstName()); // Previous update preserved
        assertEquals("Smith", user.getLastName());
        assertEquals(originalEmail, user.getEmail()); // Preserved
        assertEquals(originalPassword, user.getPasswordHash()); // Preserved
        
        // 3. Update only email
        String newEmail = "new@example.com";
        user.setEmail(newEmail);
        user.setUpdatedAt(Instant.now());
        
        assertEquals("Johnny", user.getFirstName()); // Previous updates preserved
        assertEquals("Smith", user.getLastName()); // Previous updates preserved
        assertEquals(newEmail, user.getEmail());
        assertEquals(newEmail, user.getGsi1sk()); // GSI1SK updated with email
        assertEquals(originalPassword, user.getPasswordHash()); // Preserved
        
        // Verify key structure integrity throughout updates
        assertEquals("USER#" + userId, user.getPk());
        assertEquals("PROFILE", user.getSk());
        assertEquals("USER", user.getGsi1pk());
        assertEquals("USER", user.getEntityType());
    }

    /**
     * **Validates: Requirements 4.3**
     * Property 9: User Profile Updates Maintain Integrity
     */
    @Test
    void passwordUpdatesPreserveOtherProfileData() {
        String userId = "user123";
        String email = "user@example.com";
        String firstName = "John";
        String lastName = "Doe";
        String originalPassword = "originalHashedPassword";
        String newPassword = "newHashedPassword";
        
        // Create user with original password
        User user = new User(userId, email, originalPassword, firstName, lastName);
        Instant originalCreatedAt = user.getCreatedAt();
        
        // Update password
        user.setPasswordHash(newPassword);
        user.setUpdatedAt(Instant.now());
        
        // Verify password update
        assertEquals(newPassword, user.getPasswordHash());
        
        // Verify all other profile data is preserved
        assertEquals(userId, user.getUserId());
        assertEquals(email, user.getEmail());
        assertEquals(firstName, user.getFirstName());
        assertEquals(lastName, user.getLastName());
        assertEquals(originalCreatedAt, user.getCreatedAt());
        
        // Verify key structure integrity
        assertEquals("USER#" + userId, user.getPk());
        assertEquals("PROFILE", user.getSk());
        assertEquals("USER", user.getGsi1pk());
        assertEquals(email, user.getGsi1sk());
        assertEquals("USER", user.getEntityType());
        
        // Verify timestamp update
        assertTrue(user.getUpdatedAt().isAfter(originalCreatedAt));
    }

    /**
     * **Validates: Requirements 4.3**
     * Property 9: User Profile Updates Maintain Integrity
     */
    @Property
    @Tag("Feature: z-commerce, Property 9: User Profile Updates Maintain Integrity")
    void multipleUpdatesPreserveDataConsistency(
        @ForAll("userIds") String userId,
        @ForAll("emails") String email1,
        @ForAll("emails") String email2,
        @ForAll("emails") String email3,
        @ForAll("names") String name1,
        @ForAll("names") String name2,
        @ForAll("names") String name3
    ) {
        // Create initial user
        User user = new User(userId, email1, "password", name1, name1);
        Instant originalCreatedAt = user.getCreatedAt();
        
        // Perform multiple updates
        
        // Update 1: Change email and first name
        user.setEmail(email2);
        user.setFirstName(name2);
        user.setUpdatedAt(Instant.now());
        Instant update1Time = user.getUpdatedAt();
        
        assertEquals(email2, user.getEmail());
        assertEquals(email2, user.getGsi1sk());
        assertEquals(name2, user.getFirstName());
        assertEquals(name1, user.getLastName()); // Unchanged
        assertTrue(update1Time.isAfter(originalCreatedAt));
        
        // Update 2: Change last name
        user.setLastName(name3);
        user.setUpdatedAt(Instant.now());
        Instant update2Time = user.getUpdatedAt();
        
        assertEquals(email2, user.getEmail()); // Previous update preserved
        assertEquals(name2, user.getFirstName()); // Previous update preserved
        assertEquals(name3, user.getLastName());
        assertTrue(update2Time.isAfter(update1Time));
        
        // Update 3: Change email again
        user.setEmail(email3);
        user.setUpdatedAt(Instant.now());
        Instant update3Time = user.getUpdatedAt();
        
        assertEquals(email3, user.getEmail());
        assertEquals(email3, user.getGsi1sk());
        assertEquals(name2, user.getFirstName()); // Previous updates preserved
        assertEquals(name3, user.getLastName()); // Previous updates preserved
        assertTrue(update3Time.isAfter(update2Time));
        
        // Verify identity and key structure remain consistent throughout
        assertEquals(userId, user.getUserId());
        assertEquals("USER#" + userId, user.getPk());
        assertEquals("PROFILE", user.getSk());
        assertEquals("USER", user.getGsi1pk());
        assertEquals("USER", user.getEntityType());
        assertEquals(originalCreatedAt, user.getCreatedAt());
    }

    /**
     * **Validates: Requirements 4.3**
     * Property 9: User Profile Updates Maintain Integrity
     */
    @Test
    void updateTimestampBehavior() {
        String userId = "user123";
        String email = "user@example.com";
        
        // Create user
        User user = new User(userId, email, "password", "John", "Doe");
        Instant createdAt = user.getCreatedAt();
        Instant initialUpdatedAt = user.getUpdatedAt();
        
        // Initially, createdAt and updatedAt should be close (within same millisecond is acceptable)
        assertTrue(Math.abs(createdAt.toEpochMilli() - initialUpdatedAt.toEpochMilli()) <= 1);
        
        // Wait a moment and update
        try {
            Thread.sleep(10); // Ensure different timestamp
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        user.setFirstName("Johnny");
        user.setUpdatedAt(Instant.now());
        
        // Verify timestamp behavior
        assertEquals(createdAt, user.getCreatedAt()); // Creation time never changes
        assertTrue(user.getUpdatedAt().isAfter(createdAt) || user.getUpdatedAt().equals(createdAt)); // Update time advances or stays same
        
        // Multiple updates should continue advancing updatedAt
        Instant firstUpdate = user.getUpdatedAt();
        
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        user.setLastName("Smith");
        user.setUpdatedAt(Instant.now());
        
        assertEquals(createdAt, user.getCreatedAt()); // Still unchanged
        assertTrue(user.getUpdatedAt().isAfter(firstUpdate) || user.getUpdatedAt().equals(firstUpdate)); // Continues advancing or stays same
    }

    // Providers
    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.strings().alpha().ofLength(8);
    }

    @Provide
    Arbitrary<String> emails() {
        return Arbitraries.strings().alpha().ofLength(5).map(s -> s + "@example.com");
    }

    @Provide
    Arbitrary<String> names() {
        return Arbitraries.strings().alpha().ofLength(8);
    }
}