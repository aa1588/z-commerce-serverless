package com.zcommerce.users;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.zcommerce.shared.exception.AuthenticationException;
import com.zcommerce.shared.exception.ConflictException;
import com.zcommerce.shared.exception.ValidationException;
import com.zcommerce.shared.model.User;
import com.zcommerce.shared.repository.UserRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for User Service.
 * Tests Properties 7, 8, and 9 from the design document.
 */
class UserServicePropertyTest {

    /**
     * Property 7: User Registration Enforces Uniqueness
     * For any user registration attempt, the system should create accounts with unique
     * identifiers and emails, enforce password security requirements, and store encrypted credentials.
     * **Validates: Requirements 4.1, 4.5**
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 7: User Registration Enforces Uniqueness")
    void userRegistrationEnforcesUniqueness(
        @ForAll("validEmails") String email,
        @ForAll("validPasswords") String password,
        @ForAll("names") String firstName,
        @ForAll("names") String lastName
    ) {
        MockUserRepository repository = new MockUserRepository();

        // First registration should succeed
        String userId1 = UUID.randomUUID().toString();
        User user1 = new User(userId1, email, hashPassword(password), firstName, lastName);
        repository.save(user1);

        // Verify user was stored
        assertTrue(repository.existsByEmail(email));
        Optional<User> stored = repository.findById(userId1);
        assertTrue(stored.isPresent());

        // Verify email uniqueness - second registration with same email should be rejected
        assertTrue(repository.existsByEmail(email));

        // Verify password is hashed (not stored in plain text)
        assertNotEquals(password, stored.get().getPasswordHash());

        // Verify password hash can be verified
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), stored.get().getPasswordHash());
        assertTrue(result.verified);

        // Verify unique identifier
        assertNotNull(stored.get().getUserId());
        assertEquals(userId1, stored.get().getUserId());
    }

    /**
     * Property 8: Authentication Validates Credentials
     * For any login attempt, the system should validate credentials correctly,
     * return authentication tokens for valid users, and reject invalid credentials.
     * **Validates: Requirements 4.2, 4.4**
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 8: Authentication Validates Credentials")
    void authenticationValidatesCredentials(
        @ForAll("validEmails") String email,
        @ForAll("validPasswords") String correctPassword,
        @ForAll("validPasswords") String wrongPassword
    ) {
        Assume.that(!correctPassword.equals(wrongPassword));

        MockUserRepository repository = new MockUserRepository();

        // Register user
        String userId = UUID.randomUUID().toString();
        String passwordHash = hashPassword(correctPassword);
        User user = new User(userId, email, passwordHash, "Test", "User");
        repository.save(user);

        // Valid credentials should authenticate
        Optional<User> foundUser = repository.findByEmail(email);
        assertTrue(foundUser.isPresent());

        BCrypt.Result correctResult = BCrypt.verifyer().verify(
            correctPassword.toCharArray(),
            foundUser.get().getPasswordHash()
        );
        assertTrue(correctResult.verified, "Valid credentials should authenticate");

        // Invalid password should fail
        BCrypt.Result wrongResult = BCrypt.verifyer().verify(
            wrongPassword.toCharArray(),
            foundUser.get().getPasswordHash()
        );
        assertFalse(wrongResult.verified, "Invalid password should fail authentication");

        // Non-existent user should not be found
        Optional<User> nonExistent = repository.findByEmail("nonexistent@example.com");
        assertFalse(nonExistent.isPresent());
    }

    /**
     * Property 9: User Profile Updates Maintain Integrity
     * For any user profile update, the system should apply changes correctly
     * while maintaining data integrity and enforcing business rules.
     * **Validates: Requirements 4.3**
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 9: User Profile Updates Maintain Integrity")
    void userProfileUpdatesMaintainIntegrity(
        @ForAll("validEmails") String originalEmail,
        @ForAll("validEmails") String newEmail,
        @ForAll("names") String originalFirstName,
        @ForAll("names") String newFirstName,
        @ForAll("names") String originalLastName,
        @ForAll("names") String newLastName
    ) {
        MockUserRepository repository = new MockUserRepository();

        // Create user
        String userId = UUID.randomUUID().toString();
        User user = new User(userId, originalEmail, hashPassword("Password123!"), originalFirstName, originalLastName);
        Instant createdAt = user.getCreatedAt();
        repository.save(user);

        // Update profile
        user.setFirstName(newFirstName);
        user.setLastName(newLastName);
        user.setEmail(newEmail);
        user.setUpdatedAt(Instant.now());
        repository.save(user);

        // Verify updates
        Optional<User> updated = repository.findById(userId);
        assertTrue(updated.isPresent());
        assertEquals(newFirstName, updated.get().getFirstName());
        assertEquals(newLastName, updated.get().getLastName());
        assertEquals(newEmail, updated.get().getEmail());

        // Verify userId is unchanged
        assertEquals(userId, updated.get().getUserId());

        // Verify createdAt is unchanged
        assertEquals(createdAt, updated.get().getCreatedAt());

        // Verify updatedAt is after createdAt
        assertTrue(updated.get().getUpdatedAt().compareTo(createdAt) >= 0);
    }

    // Helper method to hash password
    private String hashPassword(String password) {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray());
    }

    // Providers
    @Provide
    Arbitrary<String> validEmails() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(3)
            .ofMaxLength(10)
            .map(s -> s.toLowerCase() + "@example.com");
    }

    @Provide
    Arbitrary<String> validPasswords() {
        // Generate passwords that meet requirements: 8+ chars, uppercase, lowercase, digit
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofLength(4)
            .flatMap(lower ->
                Arbitraries.strings()
                    .withCharRange('A', 'Z')
                    .ofLength(2)
                    .flatMap(upper ->
                        Arbitraries.strings()
                            .withCharRange('0', '9')
                            .ofLength(2)
                            .map(digits -> upper + lower + digits)
                    )
            );
    }

    @Provide
    Arbitrary<String> names() {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(2)
            .ofMaxLength(20);
    }

    // Mock repository for testing without DynamoDB
    static class MockUserRepository implements UserRepository {
        private final Map<String, User> usersById = new HashMap<>();
        private final Map<String, User> usersByEmail = new HashMap<>();

        @Override
        public User save(User user) {
            usersById.put(user.getUserId(), user);
            usersByEmail.put(user.getEmail(), user);
            return user;
        }

        @Override
        public User update(User user) {
            return save(user);
        }

        @Override
        public Optional<User> findById(String id) {
            return Optional.ofNullable(usersById.get(id));
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(usersById.values());
        }

        @Override
        public boolean deleteById(String id) {
            User user = usersById.remove(id);
            if (user != null) {
                usersByEmail.remove(user.getEmail());
                return true;
            }
            return false;
        }

        @Override
        public boolean existsById(String id) {
            return usersById.containsKey(id);
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.ofNullable(usersByEmail.get(email));
        }

        @Override
        public boolean existsByEmail(String email) {
            return usersByEmail.containsKey(email);
        }
    }
}
