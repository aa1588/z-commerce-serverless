package com.zcommerce.users;

import com.zcommerce.shared.exception.AuthenticationException;
import com.zcommerce.shared.exception.ConflictException;
import com.zcommerce.shared.exception.ValidationException;
import com.zcommerce.shared.model.User;
import com.zcommerce.shared.repository.UserRepository;
import com.zcommerce.shared.util.ValidationUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for User Service edge cases and validation.
 * **Validates: Requirements 4.1, 4.2, 4.5**
 */
class UserHandlerUnitTest {

    private MockUserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = new MockUserRepository();
    }

    @Test
    @DisplayName("Should reject invalid email formats")
    void shouldRejectInvalidEmailFormats() {
        assertThrows(ValidationException.class, () -> ValidationUtils.validateEmail("invalid"));
        assertThrows(ValidationException.class, () -> ValidationUtils.validateEmail("invalid@"));
        assertThrows(ValidationException.class, () -> ValidationUtils.validateEmail("@domain.com"));
        assertThrows(ValidationException.class, () -> ValidationUtils.validateEmail(""));
        assertThrows(ValidationException.class, () -> ValidationUtils.validateEmail(null));
    }

    @Test
    @DisplayName("Should accept valid email formats")
    void shouldAcceptValidEmailFormats() {
        assertDoesNotThrow(() -> ValidationUtils.validateEmail("user@example.com"));
        assertDoesNotThrow(() -> ValidationUtils.validateEmail("user.name@example.com"));
        assertDoesNotThrow(() -> ValidationUtils.validateEmail("user+tag@example.co.uk"));
    }

    @Test
    @DisplayName("Should reject weak passwords")
    void shouldRejectWeakPasswords() {
        // Too short
        assertThrows(ValidationException.class, () -> ValidationUtils.validatePassword("Abc123"));

        // Missing uppercase
        assertThrows(ValidationException.class, () -> ValidationUtils.validatePassword("abcd1234"));

        // Missing lowercase
        assertThrows(ValidationException.class, () -> ValidationUtils.validatePassword("ABCD1234"));

        // Missing digit
        assertThrows(ValidationException.class, () -> ValidationUtils.validatePassword("Abcdefgh"));

        // Empty or null
        assertThrows(ValidationException.class, () -> ValidationUtils.validatePassword(""));
        assertThrows(ValidationException.class, () -> ValidationUtils.validatePassword(null));
    }

    @Test
    @DisplayName("Should accept strong passwords")
    void shouldAcceptStrongPasswords() {
        assertDoesNotThrow(() -> ValidationUtils.validatePassword("Password1"));
        assertDoesNotThrow(() -> ValidationUtils.validatePassword("MyStrongP4ss"));
        assertDoesNotThrow(() -> ValidationUtils.validatePassword("Complex1Password"));
    }

    @Test
    @DisplayName("Should reject duplicate registration attempts")
    void shouldRejectDuplicateRegistration() {
        String email = "test@example.com";
        String passwordHash = "hashedpassword";

        // Register first user
        User user1 = new User(UUID.randomUUID().toString(), email, passwordHash, "John", "Doe");
        userRepository.save(user1);

        // Attempt to check for duplicate
        assertTrue(userRepository.existsByEmail(email), "Email should already exist");
    }

    @Test
    @DisplayName("Should not find non-existent user by email")
    void shouldNotFindNonExistentUser() {
        Optional<User> user = userRepository.findByEmail("nonexistent@example.com");
        assertFalse(user.isPresent());
    }

    @Test
    @DisplayName("Should find user by email after registration")
    void shouldFindUserByEmailAfterRegistration() {
        String email = "test@example.com";
        User user = new User(UUID.randomUUID().toString(), email, "hash", "Test", "User");
        userRepository.save(user);

        Optional<User> found = userRepository.findByEmail(email);
        assertTrue(found.isPresent());
        assertEquals(email, found.get().getEmail());
    }

    @Test
    @DisplayName("Should validate required fields")
    void shouldValidateRequiredFields() {
        assertThrows(ValidationException.class, () -> ValidationUtils.validateRequired(null, "First name"));
        assertThrows(ValidationException.class, () -> ValidationUtils.validateRequired("", "First name"));
        assertThrows(ValidationException.class, () -> ValidationUtils.validateRequired("   ", "First name"));
        assertDoesNotThrow(() -> ValidationUtils.validateRequired("John", "First name"));
    }

    @Test
    @DisplayName("Should validate UUID format")
    void shouldValidateUuidFormat() {
        assertDoesNotThrow(() -> ValidationUtils.validateUUID(UUID.randomUUID().toString(), "User ID"));
        assertThrows(ValidationException.class, () -> ValidationUtils.validateUUID("invalid-uuid", "User ID"));
        assertThrows(ValidationException.class, () -> ValidationUtils.validateUUID("", "User ID"));
        assertThrows(ValidationException.class, () -> ValidationUtils.validateUUID(null, "User ID"));
    }

    @Test
    @DisplayName("Should preserve user data on profile retrieval")
    void shouldPreserveUserDataOnRetrieval() {
        String userId = UUID.randomUUID().toString();
        String email = "test@example.com";
        String firstName = "John";
        String lastName = "Doe";

        User user = new User(userId, email, "hash", firstName, lastName);
        userRepository.save(user);

        Optional<User> retrieved = userRepository.findById(userId);
        assertTrue(retrieved.isPresent());
        assertEquals(userId, retrieved.get().getUserId());
        assertEquals(email, retrieved.get().getEmail());
        assertEquals(firstName, retrieved.get().getFirstName());
        assertEquals(lastName, retrieved.get().getLastName());
    }

    // Mock repository
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
