package com.zcommerce.shared.repository;

import com.zcommerce.shared.model.User;

import java.util.Optional;

/**
 * Repository interface for User entities.
 */
public interface UserRepository extends Repository<User, String> {
    
    /**
     * Find a user by email address.
     * 
     * @param email The email address
     * @return Optional containing the user if found, empty otherwise
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Check if a user exists with the given email.
     * 
     * @param email The email address
     * @return true if a user exists with this email, false otherwise
     */
    boolean existsByEmail(String email);
}