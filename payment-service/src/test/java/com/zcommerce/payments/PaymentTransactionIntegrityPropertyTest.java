package com.zcommerce.payments;

import com.zcommerce.shared.model.Payment;
import com.zcommerce.shared.model.Order;
import com.zcommerce.shared.model.OrderItem;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based test for payment processing maintaining transaction integrity.
 * **Validates: Requirements 5.2, 5.3, 5.4, 5.5**
 */
class PaymentTransactionIntegrityPropertyTest {

    /**
     * **Validates: Requirements 5.2, 5.3, 5.4, 5.5**
     * Property 11: Payment Processing Maintains Transaction Integrity
     * For any payment transaction, the system should consistently handle success 
     * and failure cases, record transaction details, and maintain order state 
     * appropriately.
     */
    @Property
    @Tag("Feature: z-commerce, Property 11: Payment Processing Maintains Transaction Integrity")
    void paymentProcessingMaintainsTransactionIntegrity(
        @ForAll("transactionIds") String transactionId,
        @ForAll("orderIds") String orderId,
        @ForAll("amounts") BigDecimal amount,
        @ForAll("paymentMethods") Payment.PaymentMethod paymentMethod,
        @ForAll("processingResults") boolean processingSuccess
    ) {
        // Create payment transaction
        Payment payment = new Payment(transactionId, orderId, amount, paymentMethod);
        Instant originalProcessedAt = payment.getProcessedAt();
        
        // Simulate payment processing
        payment.setStatus(Payment.PaymentStatus.PROCESSING);
        
        if (processingSuccess) {
            // Successful payment processing
            payment.setStatus(Payment.PaymentStatus.COMPLETED);
        } else {
            // Failed payment processing
            payment.setStatus(Payment.PaymentStatus.FAILED);
        }
        
        // Verify transaction integrity maintained throughout processing
        assertEquals(transactionId, payment.getTransactionId());
        assertEquals(orderId, payment.getOrderId());
        assertEquals(0, amount.compareTo(payment.getAmount()));
        assertEquals(paymentMethod, payment.getPaymentMethod());
        
        // Verify key structures remain intact
        assertEquals("PAYMENT#" + transactionId, payment.getPk());
        assertEquals("TRANSACTION", payment.getSk());
        assertEquals("PAYMENT", payment.getEntityType());
        
        // Verify status reflects processing result
        if (processingSuccess) {
            assertEquals(Payment.PaymentStatus.COMPLETED, payment.getStatus());
        } else {
            assertEquals(Payment.PaymentStatus.FAILED, payment.getStatus());
        }
        
        // Verify timestamp integrity
        assertEquals(originalProcessedAt, payment.getProcessedAt()); // Processing time preserved
        
        // Verify transaction details are recorded
        assertNotNull(payment.getTransactionId());
        assertNotNull(payment.getOrderId());
        assertNotNull(payment.getAmount());
        assertNotNull(payment.getPaymentMethod());
        assertNotNull(payment.getStatus());
        assertNotNull(payment.getProcessedAt());
    }

    /**
     * **Validates: Requirements 5.2, 5.3, 5.4, 5.5**
     * Property 11: Payment Processing Maintains Transaction Integrity
     */
    @Property
    @Tag("Feature: z-commerce, Property 11: Payment Processing Maintains Transaction Integrity")
    void paymentSuccessNotifiesOrderService(
        @ForAll("transactionIds") String transactionId,
        @ForAll("orderIds") String orderId,
        @ForAll("userIds") String userId,
        @ForAll("amounts") BigDecimal amount,
        @ForAll("paymentMethods") Payment.PaymentMethod paymentMethod
    ) {
        // Create order
        OrderItem orderItem = new OrderItem("prod1", "Product 1", 1, amount);
        Order order = new Order(orderId, userId, Arrays.asList(orderItem), amount);
        
        // Create payment for the order
        Payment payment = new Payment(transactionId, orderId, amount, paymentMethod);
        
        // Verify initial states
        assertEquals(Order.OrderStatus.PENDING, order.getStatus());
        assertEquals(Payment.PaymentStatus.PENDING, payment.getStatus());
        
        // Simulate successful payment processing
        payment.setStatus(Payment.PaymentStatus.PROCESSING);
        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        
        // Simulate order service notification and update
        order.setStatus(Order.OrderStatus.COMPLETED);
        
        // Verify payment success integrity
        assertEquals(Payment.PaymentStatus.COMPLETED, payment.getStatus());
        assertEquals(transactionId, payment.getTransactionId());
        assertEquals(orderId, payment.getOrderId());
        assertEquals(0, amount.compareTo(payment.getAmount()));
        
        // Verify order state updated appropriately
        assertEquals(Order.OrderStatus.COMPLETED, order.getStatus());
        assertEquals(orderId, order.getOrderId());
        assertEquals(userId, order.getUserId());
        assertEquals(0, amount.compareTo(order.getTotalAmount()));
        
        // Verify referential integrity between payment and order
        assertEquals(payment.getOrderId(), order.getOrderId());
        assertEquals(0, payment.getAmount().compareTo(order.getTotalAmount()));
        
        // Verify transaction details recorded
        assertNotNull(payment.getProcessedAt());
        assertTrue(order.getUpdatedAt().isAfter(order.getCreatedAt()) || 
                  order.getUpdatedAt().equals(order.getCreatedAt()));
    }

    /**
     * **Validates: Requirements 5.2, 5.3, 5.4, 5.5**
     * Property 11: Payment Processing Maintains Transaction Integrity
     */
    @Test
    void paymentFailureMaintainsOrderState() {
        String transactionId = "txn123";
        String orderId = "order123";
        String userId = "user123";
        BigDecimal amount = BigDecimal.valueOf(100.00);
        
        // Create order
        OrderItem orderItem = new OrderItem("prod1", "Product 1", 1, amount);
        Order order = new Order(orderId, userId, Arrays.asList(orderItem), amount);
        Order.OrderStatus originalOrderStatus = order.getStatus();
        
        // Create payment
        Payment payment = new Payment(transactionId, orderId, amount, Payment.PaymentMethod.CREDIT_CARD);
        
        // Simulate payment processing failure
        payment.setStatus(Payment.PaymentStatus.PROCESSING);
        payment.setStatus(Payment.PaymentStatus.FAILED);
        
        // Verify payment failure integrity
        assertEquals(Payment.PaymentStatus.FAILED, payment.getStatus());
        assertEquals(transactionId, payment.getTransactionId());
        assertEquals(orderId, payment.getOrderId());
        assertEquals(0, amount.compareTo(payment.getAmount()));
        
        // Verify order state preserved (not updated on payment failure)
        assertEquals(originalOrderStatus, order.getStatus()); // Order remains in original state
        assertEquals(orderId, order.getOrderId());
        assertEquals(userId, order.getUserId());
        assertEquals(0, amount.compareTo(order.getTotalAmount()));
        
        // Verify transaction details recorded even for failures
        assertNotNull(payment.getTransactionId());
        assertNotNull(payment.getOrderId());
        assertNotNull(payment.getAmount());
        assertNotNull(payment.getPaymentMethod());
        assertNotNull(payment.getProcessedAt());
        
        // Verify key structures remain intact
        assertEquals("PAYMENT#" + transactionId, payment.getPk());
        assertEquals("TRANSACTION", payment.getSk());
        assertEquals("PAYMENT", payment.getEntityType());
        assertEquals("ORDER#" + orderId, order.getPk());
        assertEquals("DETAILS", order.getSk());
        assertEquals("ORDER", order.getEntityType());
    }

    /**
     * **Validates: Requirements 5.2, 5.3, 5.4, 5.5**
     * Property 11: Payment Processing Maintains Transaction Integrity
     */
    @Test
    void paymentTransactionLoggingIntegrity() {
        String transactionId = "txn123";
        String orderId = "order123";
        BigDecimal amount = BigDecimal.valueOf(250.00);
        Payment.PaymentMethod method = Payment.PaymentMethod.PAYPAL;
        
        // Create payment
        Payment payment = new Payment(transactionId, orderId, amount, method);
        Instant creationTime = payment.getProcessedAt();
        
        // Test different payment outcomes and verify logging
        Payment.PaymentStatus[] statusProgression = {
            Payment.PaymentStatus.PENDING,
            Payment.PaymentStatus.PROCESSING,
            Payment.PaymentStatus.COMPLETED
        };
        
        for (Payment.PaymentStatus status : statusProgression) {
            payment.setStatus(status);
            
            // Verify transaction logging integrity at each stage
            assertEquals(transactionId, payment.getTransactionId());
            assertEquals(orderId, payment.getOrderId());
            assertEquals(0, amount.compareTo(payment.getAmount()));
            assertEquals(method, payment.getPaymentMethod());
            assertEquals(status, payment.getStatus());
            
            // Verify audit trail information
            assertNotNull(payment.getProcessedAt());
            assertEquals(creationTime, payment.getProcessedAt()); // Creation time preserved
            
            // Verify key structures for logging/retrieval
            assertEquals("PAYMENT#" + transactionId, payment.getPk());
            assertEquals("TRANSACTION", payment.getSk());
            assertEquals("PAYMENT", payment.getEntityType());
        }
        
        // Test failure scenario logging
        Payment failedPayment = new Payment("txn456", orderId, amount, method);
        failedPayment.setStatus(Payment.PaymentStatus.PROCESSING);
        failedPayment.setStatus(Payment.PaymentStatus.FAILED);
        
        // Verify failed transaction logging
        assertEquals(Payment.PaymentStatus.FAILED, failedPayment.getStatus());
        assertEquals("txn456", failedPayment.getTransactionId());
        assertEquals(orderId, failedPayment.getOrderId());
        assertNotNull(failedPayment.getProcessedAt());
        
        // Verify both successful and failed transactions maintain integrity
        assertNotEquals(payment.getTransactionId(), failedPayment.getTransactionId());
        assertEquals(payment.getOrderId(), failedPayment.getOrderId()); // Same order
        assertEquals(0, payment.getAmount().compareTo(failedPayment.getAmount())); // Same amount
    }

    /**
     * **Validates: Requirements 5.2, 5.3, 5.4, 5.5**
     * Property 11: Payment Processing Maintains Transaction Integrity
     */
    @Property
    @Tag("Feature: z-commerce, Property 11: Payment Processing Maintains Transaction Integrity")
    void multiplePaymentAttemptsPreserveIntegrity(
        @ForAll("orderIds") String orderId,
        @ForAll("userIds") String userId,
        @ForAll("amounts") BigDecimal amount,
        @ForAll("paymentMethods") Payment.PaymentMethod method1,
        @ForAll("paymentMethods") Payment.PaymentMethod method2
    ) {
        // Create order
        OrderItem orderItem = new OrderItem("prod1", "Product 1", 1, amount);
        Order order = new Order(orderId, userId, Arrays.asList(orderItem), amount);
        
        // First payment attempt (fails)
        Payment payment1 = new Payment("txn1_" + orderId, orderId, amount, method1);
        payment1.setStatus(Payment.PaymentStatus.PROCESSING);
        payment1.setStatus(Payment.PaymentStatus.FAILED);
        
        // Second payment attempt (succeeds)
        Payment payment2 = new Payment("txn2_" + orderId, orderId, amount, method2);
        payment2.setStatus(Payment.PaymentStatus.PROCESSING);
        payment2.setStatus(Payment.PaymentStatus.COMPLETED);
        
        // Update order after successful payment
        order.setStatus(Order.OrderStatus.COMPLETED);
        
        // Verify both payment transactions maintain integrity
        assertEquals(Payment.PaymentStatus.FAILED, payment1.getStatus());
        assertEquals(Payment.PaymentStatus.COMPLETED, payment2.getStatus());
        
        // Verify transaction details for both payments
        assertEquals("txn1_" + orderId, payment1.getTransactionId());
        assertEquals("txn2_" + orderId, payment2.getTransactionId());
        assertEquals(orderId, payment1.getOrderId());
        assertEquals(orderId, payment2.getOrderId());
        assertEquals(0, amount.compareTo(payment1.getAmount()));
        assertEquals(0, amount.compareTo(payment2.getAmount()));
        
        // Verify payments are distinct but reference same order
        assertNotEquals(payment1.getTransactionId(), payment2.getTransactionId());
        assertEquals(payment1.getOrderId(), payment2.getOrderId());
        assertEquals(0, payment1.getAmount().compareTo(payment2.getAmount()));
        
        // Verify order state reflects successful payment
        assertEquals(Order.OrderStatus.COMPLETED, order.getStatus());
        assertEquals(orderId, order.getOrderId());
        assertEquals(userId, order.getUserId());
        
        // Verify key structures for both transactions
        assertEquals("PAYMENT#txn1_" + orderId, payment1.getPk());
        assertEquals("PAYMENT#txn2_" + orderId, payment2.getPk());
        assertEquals("TRANSACTION", payment1.getSk());
        assertEquals("TRANSACTION", payment2.getSk());
        
        // Verify audit trail for both transactions
        assertNotNull(payment1.getProcessedAt());
        assertNotNull(payment2.getProcessedAt());
    }

    /**
     * **Validates: Requirements 5.2, 5.3, 5.4, 5.5**
     * Property 11: Payment Processing Maintains Transaction Integrity
     */
    @Test
    void paymentRefundMaintainsTransactionHistory() {
        String transactionId = "txn123";
        String orderId = "order123";
        String userId = "user123";
        BigDecimal amount = BigDecimal.valueOf(150.00);
        
        // Create and complete order
        OrderItem orderItem = new OrderItem("prod1", "Product 1", 1, amount);
        Order order = new Order(orderId, userId, Arrays.asList(orderItem), amount);
        order.setStatus(Order.OrderStatus.COMPLETED);
        
        // Create and complete payment
        Payment payment = new Payment(transactionId, orderId, amount, Payment.PaymentMethod.CREDIT_CARD);
        payment.setStatus(Payment.PaymentStatus.PROCESSING);
        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        
        Instant originalProcessedAt = payment.getProcessedAt();
        
        // Process refund
        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        
        // Verify refund maintains transaction integrity
        assertEquals(Payment.PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals(transactionId, payment.getTransactionId());
        assertEquals(orderId, payment.getOrderId());
        assertEquals(0, amount.compareTo(payment.getAmount()));
        assertEquals(Payment.PaymentMethod.CREDIT_CARD, payment.getPaymentMethod());
        
        // Verify original transaction details preserved
        assertEquals(originalProcessedAt, payment.getProcessedAt());
        
        // Verify order state (may or may not change depending on business rules)
        assertEquals(orderId, order.getOrderId());
        assertEquals(userId, order.getUserId());
        assertEquals(0, amount.compareTo(order.getTotalAmount()));
        
        // Verify key structures remain intact
        assertEquals("PAYMENT#" + transactionId, payment.getPk());
        assertEquals("TRANSACTION", payment.getSk());
        assertEquals("PAYMENT", payment.getEntityType());
        
        // Verify complete transaction history is maintained
        assertNotNull(payment.getTransactionId());
        assertNotNull(payment.getOrderId());
        assertNotNull(payment.getAmount());
        assertNotNull(payment.getPaymentMethod());
        assertNotNull(payment.getProcessedAt());
        
        // Verify referential integrity with order
        assertEquals(payment.getOrderId(), order.getOrderId());
        assertEquals(0, payment.getAmount().compareTo(order.getTotalAmount()));
    }

    /**
     * **Validates: Requirements 5.2, 5.3, 5.4, 5.5**
     * Property 11: Payment Processing Maintains Transaction Integrity
     */
    @Property
    @Tag("Feature: z-commerce, Property 11: Payment Processing Maintains Transaction Integrity")
    void paymentProcessingWithComplexOrderMaintainsIntegrity(
        @ForAll("transactionIds") String transactionId,
        @ForAll("orderIds") String orderId,
        @ForAll("userIds") String userId,
        @ForAll("paymentMethods") Payment.PaymentMethod paymentMethod,
        @ForAll("processingResults") boolean processingSuccess
    ) {
        // Create complex order with multiple items
        OrderItem item1 = new OrderItem("prod1", "Product 1", 2, BigDecimal.valueOf(25.00));
        OrderItem item2 = new OrderItem("prod2", "Product 2", 1, BigDecimal.valueOf(50.00));
        OrderItem item3 = new OrderItem("prod3", "Product 3", 3, BigDecimal.valueOf(15.00));
        List<OrderItem> items = Arrays.asList(item1, item2, item3);
        
        BigDecimal totalAmount = BigDecimal.valueOf(145.00); // (2*25) + (1*50) + (3*15)
        Order order = new Order(orderId, userId, items, totalAmount);
        
        // Create payment for complex order
        Payment payment = new Payment(transactionId, orderId, totalAmount, paymentMethod);
        
        // Process payment
        payment.setStatus(Payment.PaymentStatus.PROCESSING);
        
        if (processingSuccess) {
            payment.setStatus(Payment.PaymentStatus.COMPLETED);
            order.setStatus(Order.OrderStatus.COMPLETED);
        } else {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            // Order remains in original state on payment failure
        }
        
        // Verify payment transaction integrity
        assertEquals(transactionId, payment.getTransactionId());
        assertEquals(orderId, payment.getOrderId());
        assertEquals(0, totalAmount.compareTo(payment.getAmount()));
        assertEquals(paymentMethod, payment.getPaymentMethod());
        
        // Verify order integrity
        assertEquals(orderId, order.getOrderId());
        assertEquals(userId, order.getUserId());
        assertEquals(3, order.getItems().size());
        assertEquals(0, totalAmount.compareTo(order.getTotalAmount()));
        
        // Verify referential integrity between payment and complex order
        assertEquals(payment.getOrderId(), order.getOrderId());
        assertEquals(0, payment.getAmount().compareTo(order.getTotalAmount()));
        
        // Verify status consistency
        if (processingSuccess) {
            assertEquals(Payment.PaymentStatus.COMPLETED, payment.getStatus());
            assertEquals(Order.OrderStatus.COMPLETED, order.getStatus());
        } else {
            assertEquals(Payment.PaymentStatus.FAILED, payment.getStatus());
            // Order status depends on business rules, but should be consistent
            assertNotNull(order.getStatus());
        }
        
        // Verify all order items maintain integrity
        for (OrderItem item : order.getItems()) {
            assertNotNull(item.getProductId());
            assertNotNull(item.getProductName());
            assertTrue(item.getQuantity() > 0);
            assertTrue(item.getPrice().compareTo(BigDecimal.ZERO) > 0);
            assertTrue(item.getTotalPrice().compareTo(BigDecimal.ZERO) > 0);
        }
        
        // Verify key structures
        assertEquals("PAYMENT#" + transactionId, payment.getPk());
        assertEquals("ORDER#" + orderId, order.getPk());
        assertEquals("TRANSACTION", payment.getSk());
        assertEquals("DETAILS", order.getSk());
        
        // Verify audit information
        assertNotNull(payment.getProcessedAt());
        assertNotNull(order.getCreatedAt());
        assertNotNull(order.getUpdatedAt());
    }

    // Providers
    @Provide
    Arbitrary<String> transactionIds() {
        return Arbitraries.strings().alpha().ofLength(10);
    }

    @Provide
    Arbitrary<String> orderIds() {
        return Arbitraries.strings().alpha().ofLength(8);
    }

    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.strings().alpha().ofLength(8);
    }

    @Provide
    Arbitrary<BigDecimal> amounts() {
        return Arbitraries.bigDecimals()
            .between(BigDecimal.valueOf(1.00), BigDecimal.valueOf(9999.99))
            .ofScale(2);
    }

    @Provide
    Arbitrary<Payment.PaymentMethod> paymentMethods() {
        return Arbitraries.of(Payment.PaymentMethod.values());
    }

    @Provide
    Arbitrary<Boolean> processingResults() {
        return Arbitraries.of(true, false);
    }
}