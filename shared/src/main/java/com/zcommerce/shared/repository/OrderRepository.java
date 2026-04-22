package com.zcommerce.shared.repository;

import com.zcommerce.shared.model.Order;

import java.util.List;

/**
 * Repository interface for Order entities.
 */
public interface OrderRepository extends Repository<Order, String> {
    
    /**
     * Find all orders for a specific user.
     * 
     * @param userId The user ID
     * @return List of orders for the user, sorted by creation date (newest first)
     */
    List<Order> findByUserId(String userId);
    
    /**
     * Find orders by status.
     * 
     * @param status The order status
     * @return List of orders with the specified status
     */
    List<Order> findByStatus(Order.OrderStatus status);
    
    /**
     * Update order status.
     * 
     * @param orderId The order ID
     * @param status The new status
     * @return true if update was successful, false if order not found
     */
    boolean updateStatus(String orderId, Order.OrderStatus status);
}