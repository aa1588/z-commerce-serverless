package com.zcommerce.shared.repository;

import com.zcommerce.shared.model.Payment;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Payment entities.
 */
public interface PaymentRepository extends Repository<Payment, String> {
    
    /**
     * Find payment by order ID.
     * 
     * @param orderId The order ID
     * @return Optional containing the payment if found, empty otherwise
     */
    Optional<Payment> findByOrderId(String orderId);
    
    /**
     * Find payments by status.
     * 
     * @param status The payment status
     * @return List of payments with the specified status
     */
    List<Payment> findByStatus(Payment.PaymentStatus status);
    
    /**
     * Update payment status.
     * 
     * @param transactionId The transaction ID
     * @param status The new status
     * @return true if update was successful, false if payment not found
     */
    boolean updateStatus(String transactionId, Payment.PaymentStatus status);
}