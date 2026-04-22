package com.zcommerce.shared.repository;

import com.zcommerce.shared.model.Product;

import java.util.List;

/**
 * Repository interface for Product entities.
 */
public interface ProductRepository extends Repository<Product, String> {
    
    /**
     * Find products by category.
     * 
     * @param category The product category
     * @return List of products in the specified category
     */
    List<Product> findByCategory(String category);
    
    /**
     * Find products that are available (inventory > 0).
     * 
     * @return List of available products
     */
    List<Product> findAvailableProducts();
    
    /**
     * Update product inventory.
     * 
     * @param productId The product ID
     * @param newInventory The new inventory count
     * @return true if update was successful, false if product not found
     */
    boolean updateInventory(String productId, Integer newInventory);
    
    /**
     * Decrease product inventory by the specified amount.
     * 
     * @param productId The product ID
     * @param quantity The quantity to decrease
     * @return true if update was successful, false if insufficient inventory or product not found
     */
    boolean decreaseInventory(String productId, Integer quantity);
}