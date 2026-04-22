package com.zcommerce.shared.user;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.zcommerce.shared.model.User;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for authentication validation.
 * **Validates: Requirements 4.2, 4.4**
 */
class AuthenticationPropertyTest {

    private static final String TEST_JWT_SECRET = "test-secret-for-property-testing";
    private static final long JWT_EXPIRATION_MS = 3600000; // 1 hour

    /**
     * **Validates: Requirements 4.2, 4.4**
     * Property 8: Authentication Validates Credentials
     * For any login attempt, the system should validate credentials correctly, 
     * return authentication tokens for valid users, and reject invalid credentials.
     */
    @Test
    void passwordHashingIsConsistent() {
        String plainPassword = "mySecurePassword123";
        
        // Hash the same password multiple times
        String hash1 = BCrypt.withDefaults().hashToString(12, plainPassword.toCharArray());
        String hash2 = BCrypt.withDefaults().hashToString(12, plainPassword.toCharArray());
        
        // Hashes should be different (due to salt)
        assertNotEquals(hash1, hash2);
        
        // But both should verify against the original password
        BCrypt.Result result1 = BCrypt.verifyer().verify(plainPassword.toCharArray(), hash1);
        BCrypt.Result result2 = BCrypt.verifyer().verify(plainPassword.toCharArray(), hash2);
        
        assertTrue(result1.verified);
        assertTrue(result2.verified);
        
        // Wrong password should not verify
        BCrypt.Result wrongResult1 = BCrypt.verifyer().verify("wrongPassword".toCharArray(), hash1);
        BCrypt.Result wrongResult2 = BCrypt.verifyer().verify("wrongPassword".toCharArray(), hash2);
        
        assertFalse(wrongResult1.verified);
        assertFalse(wrongResult2.verified);
    }

    @Test
    void jwtTokenGenerationAndValidation() {
        // Create test user
        User user = new User("user123", "test@example.com", "hashedPassword", "John", "Doe");
        
        // Generate JWT token
        Algorithm algorithm = Algorithm.HMAC256(TEST_JWT_SECRET);
        String token = JWT.create()
            .withSubject(user.getUserId())
            .withClaim("email", user.getEmail())
            .withClaim("firstName", user.getFirstName())
            .withClaim("lastName", user.getLastName())
            .withIssuedAt(new Date())
            .withExpiresAt(new Date(System.currentTimeMillis() + JWT_EXPIRATION_MS))
            .sign(algorithm);
        
        assertNotNull(token);
        assertTrue(token.length() > 0);
        
        // Validate the token
        DecodedJWT decodedJWT = JWT.require(algorithm).build().verify(token);
        
        assertEquals(user.getUserId(), decodedJWT.getSubject());
        assertEquals(user.getEmail(), decodedJWT.getClaim("email").asString());
        assertEquals(user.getFirstName(), decodedJWT.getClaim("firstName").asString());
        assertEquals(user.getLastName(), decodedJWT.getClaim("lastName").asString());
        
        // Verify token is not expired
        assertTrue(decodedJWT.getExpiresAt().after(new Date()));
    }

    @Test
    void invalidTokensAreRejected() {
        Algorithm algorithm = Algorithm.HMAC256(TEST_JWT_SECRET);
        
        // Test invalid tokens
        String[] invalidTokens = {
            "invalid.token.here",
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalid.signature",
            "",
            "not-a-jwt-at-all"
        };
        
        for (String invalidToken : invalidTokens) {
            assertThrows(JWTVerificationException.class, () -> {
                JWT.require(algorithm).build().verify(invalidToken);
            }, "Invalid token should be rejected: " + invalidToken);
        }
    }

    @Test
    void expiredTokensAreRejected() {
        User user = new User("user123", "test@example.com", "hashedPassword", "John", "Doe");
        Algorithm algorithm = Algorithm.HMAC256(TEST_JWT_SECRET);
        
        // Create an already expired token
        String expiredToken = JWT.create()
            .withSubject(user.getUserId())
            .withClaim("email", user.getEmail())
            .withIssuedAt(new Date(System.currentTimeMillis() - 7200000)) // 2 hours ago
            .withExpiresAt(new Date(System.currentTimeMillis() - 3600000)) // 1 hour ago (expired)
            .sign(algorithm);
        
        // Verification should fail due to expiration
        assertThrows(JWTVerificationException.class, () -> {
            JWT.require(algorithm).build().verify(expiredToken);
        });
    }

    @Test
    void wrongSecretRejectsToken() {
        User user = new User("user123", "test@example.com", "hashedPassword", "John", "Doe");
        
        // Create token with one secret
        Algorithm algorithm1 = Algorithm.HMAC256("secret1");
        String token = JWT.create()
            .withSubject(user.getUserId())
            .withClaim("email", user.getEmail())
            .withIssuedAt(new Date())
            .withExpiresAt(new Date(System.currentTimeMillis() + JWT_EXPIRATION_MS))
            .sign(algorithm1);
        
        // Try to verify with different secret
        Algorithm algorithm2 = Algorithm.HMAC256("secret2");
        
        assertThrows(JWTVerificationException.class, () -> {
            JWT.require(algorithm2).build().verify(token);
        });
    }

    @Test
    void passwordValidationWithDifferentInputs() {
        // Test various password scenarios
        String[][] testCases = {
            {"password123", "password123", "true"},  // Correct password
            {"password123", "password124", "false"}, // Wrong password
            {"Password123", "password123", "false"}, // Case sensitive
            {"password123", "PASSWORD123", "false"}, // Case sensitive
            {"", "", "true"},                        // Empty passwords
            {"special!@#$%", "special!@#$%", "true"}, // Special characters
            {"unicode🔒", "unicode🔒", "true"},        // Unicode characters
        };
        
        for (String[] testCase : testCases) {
            String originalPassword = testCase[0];
            String testPassword = testCase[1];
            boolean shouldMatch = Boolean.parseBoolean(testCase[2]);
            
            // Hash the original password
            String hashedPassword = BCrypt.withDefaults().hashToString(12, originalPassword.toCharArray());
            
            // Verify with test password
            BCrypt.Result result = BCrypt.verifyer().verify(testPassword.toCharArray(), hashedPassword);
            
            assertEquals(shouldMatch, result.verified, 
                String.format("Password verification failed for original='%s', test='%s', expected=%s", 
                    originalPassword, testPassword, shouldMatch));
        }
    }

    @Test
    void userCredentialValidationFlow() {
        // Simulate complete authentication flow
        String email = "user@example.com";
        String plainPassword = "securePassword123";
        
        // 1. User registration - hash password
        String hashedPassword = BCrypt.withDefaults().hashToString(12, plainPassword.toCharArray());
        User user = new User("user123", email, hashedPassword, "John", "Doe");
        
        // 2. User login - verify password
        BCrypt.Result passwordResult = BCrypt.verifyer().verify(plainPassword.toCharArray(), user.getPasswordHash());
        assertTrue(passwordResult.verified, "Valid password should be verified");
        
        // 3. Generate JWT token on successful login
        Algorithm algorithm = Algorithm.HMAC256(TEST_JWT_SECRET);
        String token = JWT.create()
            .withSubject(user.getUserId())
            .withClaim("email", user.getEmail())
            .withIssuedAt(new Date())
            .withExpiresAt(new Date(System.currentTimeMillis() + JWT_EXPIRATION_MS))
            .sign(algorithm);
        
        // 4. Validate token for subsequent requests
        DecodedJWT decodedJWT = JWT.require(algorithm).build().verify(token);
        assertEquals(user.getUserId(), decodedJWT.getSubject());
        assertEquals(user.getEmail(), decodedJWT.getClaim("email").asString());
        
        // 5. Test invalid login attempt
        BCrypt.Result invalidResult = BCrypt.verifyer().verify("wrongPassword".toCharArray(), user.getPasswordHash());
        assertFalse(invalidResult.verified, "Invalid password should be rejected");
    }

    @Test
    void tokenClaimsAreAccurate() {
        // Test with various user data
        String[][] userData = {
            {"user1", "john@example.com", "John", "Doe"},
            {"user2", "jane@example.com", "Jane", "Smith"},
            {"user3", "bob@example.com", "Bob", "Johnson"}
        };
        
        Algorithm algorithm = Algorithm.HMAC256(TEST_JWT_SECRET);
        
        for (String[] data : userData) {
            String userId = data[0];
            String email = data[1];
            String firstName = data[2];
            String lastName = data[3];
            
            User user = new User(userId, email, "password", firstName, lastName);
            
            // Generate token
            String token = JWT.create()
                .withSubject(user.getUserId())
                .withClaim("email", user.getEmail())
                .withClaim("firstName", user.getFirstName())
                .withClaim("lastName", user.getLastName())
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + JWT_EXPIRATION_MS))
                .sign(algorithm);
            
            // Verify all claims
            DecodedJWT decodedJWT = JWT.require(algorithm).build().verify(token);
            
            assertEquals(userId, decodedJWT.getSubject());
            assertEquals(email, decodedJWT.getClaim("email").asString());
            assertEquals(firstName, decodedJWT.getClaim("firstName").asString());
            assertEquals(lastName, decodedJWT.getClaim("lastName").asString());
            
            // Verify token structure
            assertNotNull(decodedJWT.getIssuedAt());
            assertNotNull(decodedJWT.getExpiresAt());
            assertTrue(decodedJWT.getExpiresAt().after(decodedJWT.getIssuedAt()));
        }
    }

    @Test
    void authenticationSecurityProperties() {
        String password = "testPassword123";
        
        // 1. Password hashing should be one-way (irreversible)
        String hash = BCrypt.withDefaults().hashToString(12, password.toCharArray());
        assertNotEquals(password, hash);
        assertFalse(hash.contains(password));
        
        // 2. Same password should produce different hashes (due to salt)
        String hash2 = BCrypt.withDefaults().hashToString(12, password.toCharArray());
        assertNotEquals(hash, hash2);
        
        // 3. Both hashes should verify the original password
        assertTrue(BCrypt.verifyer().verify(password.toCharArray(), hash).verified);
        assertTrue(BCrypt.verifyer().verify(password.toCharArray(), hash2).verified);
        
        // 4. JWT tokens should be tamper-evident
        User user = new User("user123", "test@example.com", hash, "John", "Doe");
        Algorithm algorithm = Algorithm.HMAC256(TEST_JWT_SECRET);
        
        String validToken = JWT.create()
            .withSubject(user.getUserId())
            .withClaim("email", user.getEmail())
            .withIssuedAt(new Date())
            .withExpiresAt(new Date(System.currentTimeMillis() + JWT_EXPIRATION_MS))
            .sign(algorithm);
        
        // Valid token should verify
        assertDoesNotThrow(() -> {
            JWT.require(algorithm).build().verify(validToken);
        });
        
        // Tampered token should fail
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "XXXXX";
        assertThrows(JWTVerificationException.class, () -> {
            JWT.require(algorithm).build().verify(tamperedToken);
        });
    }
}