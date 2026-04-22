package com.zcommerce.shared.repository;

import com.zcommerce.shared.model.CartItem;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for CartItem entities.
 */
public interface CartRepository extends Repository<CartItem, String> {
    
    /**
     * Find all cart items for a specific user.
     * 
     * @param userId The user ID
     * @return List of cart items for the user
     */
    List<CartItem> findByUserId(String userId);
    
    /**
     * Find a specific cart item for a user and product.
     * 
     * @param userId The user ID
     * @param productId The product ID
     * @return Optional containing the cart item if found, empty otherwise
     */
    Optional<CartItem> findByUserIdAndProductId(String userId, String productId);
    
    /**
     * Delete all cart items for a specific user.
     * 
     * @param userId The user ID
     * @return Number of items deleted
     */
    int deleteByUserId(String userId);
    
    /**
     * Delete a specific cart item for a user and product.
     * 
     * @param userId The user ID
     * @param productId The product ID
     * @return true if the item was deleted, false if it didn't exist
     */
    boolean deleteByUserIdAndProductId(String userId, String productId);
    
    /**
     * Delete all cart items for a specific product (used when product is deleted).
     * 
     * @param productId The product ID
     * @return Number of items deleted
     */
    int deleteByProductId(String productId);
}