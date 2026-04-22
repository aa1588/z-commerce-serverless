package com.zcommerce.shared.repository.impl;

import com.zcommerce.shared.dynamodb.KeyBuilder;
import com.zcommerce.shared.model.User;
import com.zcommerce.shared.repository.DynamoDbRepository;
import com.zcommerce.shared.repository.UserRepository;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.Map;
import java.util.Optional;

/**
 * DynamoDB implementation of UserRepository.
 */
public class DynamoDbUserRepository extends DynamoDbRepository<User> implements UserRepository {

    public DynamoDbUserRepository() {
        super(User.class, "USER");
    }

    @Override
    protected Key buildKey(String userId) {
        return Key.builder()
            .partitionValue(KeyBuilder.userPK(userId))
            .sortValue(KeyBuilder.userProfileSK())
            .build();
    }

    @Override
    protected String extractId(User user) {
        return user.getUserId();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        try {
            // For now, use scan to find by email - can be optimized with GSI later
            return findAll().stream()
                .filter(user -> email.equals(user.getEmail()))
                .findFirst();
        } catch (Exception e) {
            logger.error("user_find_by_email_error", Map.of(
                "email", email,
                "error", e.getMessage()
            ), e);
            throw new RuntimeException("Failed to find user by email: " + email, e);
        }
    }

    @Override
    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }
}