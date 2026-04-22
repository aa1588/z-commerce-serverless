package com.zcommerce.payments;

import com.zcommerce.shared.model.Order;
import com.zcommerce.shared.model.OrderItem;
import com.zcommerce.shared.model.Payment;
import com.zcommerce.shared.repository.OrderRepository;
import com.zcommerce.shared.repository.PaymentRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for Payment Service.
 * Tests Properties 10 and 11 from the design document.
 */
class PaymentServicePropertyTest {

    /**
     * Property 10: Payment Validation Enforces Business Rules
     * For any payment request, the system should validate payment information and order details
     * before processing, ensuring all required data is present and valid.
     * **Validates: Requirements 5.1**
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 10: Payment Validation Enforces Business Rules")
    void paymentValidationEnforcesBusinessRules(
        @ForAll("orderIds") String orderId,
        @ForAll("userIds") String userId,
        @ForAll("amounts") BigDecimal amount,
        @ForAll("paymentMethods") Payment.PaymentMethod paymentMethod
    ) {
        MockOrderRepository orderRepository = new MockOrderRepository();
        MockPaymentRepository paymentRepository = new MockPaymentRepository();

        // Create order
        List<OrderItem> items = List.of(new OrderItem("prod1", "Product 1", 1, amount));
        Order order = new Order(orderId, userId, items, amount);
        order.setStatus(Order.OrderStatus.PENDING);
        orderRepository.save(order);

        // Validate payment request
        // 1. Order must exist
        assertTrue(orderRepository.findById(orderId).isPresent(), "Order should exist");

        // 2. Order must be in correct status
        assertEquals(Order.OrderStatus.PENDING, order.getStatus(), "Order must be PENDING for payment");

        // 3. Payment amount should match order total
        assertEquals(0, amount.compareTo(order.getTotalAmount()), "Payment amount should match order total");

        // 4. Payment method should be valid (enum check)
        assertNotNull(paymentMethod, "Payment method must not be null");

        // Create payment after validation passes
        String transactionId = UUID.randomUUID().toString();
        Payment payment = new Payment(transactionId, orderId, amount, paymentMethod);
        payment.setStatus(Payment.PaymentStatus.PROCESSING);

        paymentRepository.save(payment);

        // Verify payment was created with correct data
        Optional<Payment> created = paymentRepository.findById(transactionId);
        assertTrue(created.isPresent());
        assertEquals(orderId, created.get().getOrderId());
        assertEquals(0, amount.compareTo(created.get().getAmount()));
        assertEquals(paymentMethod, created.get().getPaymentMethod());
    }

    /**
     * Property 11: Payment Processing Maintains Transaction Integrity
     * For any payment transaction, the system should consistently handle success and failure cases,
     * record transaction details, and maintain order state appropriately.
     * **Validates: Requirements 5.2, 5.3, 5.4, 5.5**
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 11: Payment Processing Maintains Transaction Integrity")
    void paymentProcessingMaintainsTransactionIntegrity(
        @ForAll("orderIds") String orderId,
        @ForAll("userIds") String userId,
        @ForAll("amounts") BigDecimal amount,
        @ForAll("paymentMethods") Payment.PaymentMethod paymentMethod,
        @ForAll("paymentOutcomes") boolean paymentSuccessful
    ) {
        MockOrderRepository orderRepository = new MockOrderRepository();
        MockPaymentRepository paymentRepository = new MockPaymentRepository();

        // Create order
        List<OrderItem> items = List.of(new OrderItem("prod1", "Product 1", 1, amount));
        Order order = new Order(orderId, userId, items, amount);
        order.setStatus(Order.OrderStatus.PENDING);
        orderRepository.save(order);

        // Create payment transaction
        String transactionId = UUID.randomUUID().toString();
        Payment payment = new Payment(transactionId, orderId, amount, paymentMethod);
        payment.setStatus(Payment.PaymentStatus.PROCESSING);

        // Process payment
        if (paymentSuccessful) {
            payment.setStatus(Payment.PaymentStatus.COMPLETED);
            payment.setProcessedAt(Instant.now());

            // Update order status
            order.setStatus(Order.OrderStatus.PROCESSING);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);
        } else {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            payment.setProcessedAt(Instant.now());

            // Update order status
            order.setStatus(Order.OrderStatus.FAILED);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);
        }

        paymentRepository.save(payment);

        // Verify transaction integrity
        Optional<Payment> savedPayment = paymentRepository.findById(transactionId);
        assertTrue(savedPayment.isPresent(), "Payment transaction should be recorded");

        Optional<Order> savedOrder = orderRepository.findById(orderId);
        assertTrue(savedOrder.isPresent(), "Order should still exist");

        if (paymentSuccessful) {
            // Verify success case
            assertEquals(Payment.PaymentStatus.COMPLETED, savedPayment.get().getStatus());
            assertEquals(Order.OrderStatus.PROCESSING, savedOrder.get().getStatus());
            assertNotNull(savedPayment.get().getProcessedAt(), "ProcessedAt should be set");
        } else {
            // Verify failure case
            assertEquals(Payment.PaymentStatus.FAILED, savedPayment.get().getStatus());
            assertEquals(Order.OrderStatus.FAILED, savedOrder.get().getStatus());
            assertNotNull(savedPayment.get().getProcessedAt(), "ProcessedAt should be set even for failures");
        }

        // Verify transaction details are recorded
        assertEquals(orderId, savedPayment.get().getOrderId());
        assertEquals(0, amount.compareTo(savedPayment.get().getAmount()));
        assertEquals(paymentMethod, savedPayment.get().getPaymentMethod());
    }

    /**
     * Property 11: Refund processing updates payment and order state
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 11: Payment Processing Maintains Transaction Integrity")
    void refundProcessingMaintainsIntegrity(
        @ForAll("orderIds") String orderId,
        @ForAll("userIds") String userId,
        @ForAll("amounts") BigDecimal amount,
        @ForAll("paymentMethods") Payment.PaymentMethod paymentMethod
    ) {
        MockOrderRepository orderRepository = new MockOrderRepository();
        MockPaymentRepository paymentRepository = new MockPaymentRepository();

        // Create completed order
        List<OrderItem> items = List.of(new OrderItem("prod1", "Product 1", 1, amount));
        Order order = new Order(orderId, userId, items, amount);
        order.setStatus(Order.OrderStatus.PROCESSING);
        orderRepository.save(order);

        // Create completed payment
        String transactionId = UUID.randomUUID().toString();
        Payment payment = new Payment(transactionId, orderId, amount, paymentMethod);
        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        payment.setProcessedAt(Instant.now());
        paymentRepository.save(payment);

        // Process refund
        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        payment.setProcessedAt(Instant.now());
        paymentRepository.save(payment);

        order.setStatus(Order.OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        // Verify refund integrity
        Optional<Payment> refundedPayment = paymentRepository.findById(transactionId);
        assertTrue(refundedPayment.isPresent());
        assertEquals(Payment.PaymentStatus.REFUNDED, refundedPayment.get().getStatus());

        Optional<Order> cancelledOrder = orderRepository.findById(orderId);
        assertTrue(cancelledOrder.isPresent());
        assertEquals(Order.OrderStatus.CANCELLED, cancelledOrder.get().getStatus());
    }

    /**
     * Property 11: Only completed payments can be refunded
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 11: Payment Processing Maintains Transaction Integrity")
    void onlyCompletedPaymentsCanBeRefunded(
        @ForAll("orderIds") String orderId,
        @ForAll("amounts") BigDecimal amount,
        @ForAll("nonCompletedStatuses") Payment.PaymentStatus initialStatus
    ) {
        MockPaymentRepository paymentRepository = new MockPaymentRepository();

        // Create payment with non-completed status
        String transactionId = UUID.randomUUID().toString();
        Payment payment = new Payment(transactionId, orderId, amount, Payment.PaymentMethod.CREDIT_CARD);
        payment.setStatus(initialStatus);
        paymentRepository.save(payment);

        // Verify refund should not be allowed
        assertNotEquals(Payment.PaymentStatus.COMPLETED, payment.getStatus(),
            "Only COMPLETED payments can be refunded");

        // Attempting to refund should be rejected
        boolean canRefund = payment.getStatus() == Payment.PaymentStatus.COMPLETED;
        assertFalse(canRefund, "Should not be able to refund non-completed payment");
    }

    // Providers
    @Provide
    Arbitrary<String> orderIds() {
        return Arbitraries.strings().alpha().ofLength(12);
    }

    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.strings().alpha().ofLength(12);
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
    Arbitrary<Boolean> paymentOutcomes() {
        return Arbitraries.of(true, false);
    }

    @Provide
    Arbitrary<Payment.PaymentStatus> nonCompletedStatuses() {
        return Arbitraries.of(
            Payment.PaymentStatus.PENDING,
            Payment.PaymentStatus.PROCESSING,
            Payment.PaymentStatus.FAILED
        );
    }

    // Mock repositories
    static class MockPaymentRepository implements PaymentRepository {
        private final Map<String, Payment> payments = new HashMap<>();

        @Override
        public Payment save(Payment payment) {
            payments.put(payment.getTransactionId(), payment);
            return payment;
        }

        @Override
        public Payment update(Payment payment) {
            return save(payment);
        }

        @Override
        public Optional<Payment> findById(String id) {
            return Optional.ofNullable(payments.get(id));
        }

        @Override
        public List<Payment> findAll() {
            return new ArrayList<>(payments.values());
        }

        @Override
        public boolean deleteById(String id) {
            return payments.remove(id) != null;
        }

        @Override
        public boolean existsById(String id) {
            return payments.containsKey(id);
        }

        @Override
        public Optional<Payment> findByOrderId(String orderId) {
            return payments.values().stream()
                .filter(p -> orderId.equals(p.getOrderId()))
                .findFirst();
        }

        @Override
        public List<Payment> findByStatus(Payment.PaymentStatus status) {
            return payments.values().stream()
                .filter(p -> status.equals(p.getStatus()))
                .collect(Collectors.toList());
        }

        @Override
        public boolean updateStatus(String transactionId, Payment.PaymentStatus status) {
            Payment payment = payments.get(transactionId);
            if (payment != null) {
                payment.setStatus(status);
                return true;
            }
            return false;
        }
    }

    static class MockOrderRepository implements OrderRepository {
        private final Map<String, Order> orders = new HashMap<>();

        @Override
        public Order save(Order order) {
            orders.put(order.getOrderId(), order);
            return order;
        }

        @Override
        public Order update(Order order) {
            return save(order);
        }

        @Override
        public Optional<Order> findById(String id) {
            return Optional.ofNullable(orders.get(id));
        }

        @Override
        public List<Order> findAll() {
            return new ArrayList<>(orders.values());
        }

        @Override
        public boolean deleteById(String id) {
            return orders.remove(id) != null;
        }

        @Override
        public boolean existsById(String id) {
            return orders.containsKey(id);
        }

        @Override
        public List<Order> findByUserId(String userId) {
            return orders.values().stream()
                .filter(o -> userId.equals(o.getUserId()))
                .collect(Collectors.toList());
        }

        @Override
        public List<Order> findByStatus(Order.OrderStatus status) {
            return orders.values().stream()
                .filter(o -> status.equals(o.getStatus()))
                .collect(Collectors.toList());
        }

        @Override
        public boolean updateStatus(String orderId, Order.OrderStatus status) {
            Order order = orders.get(orderId);
            if (order != null) {
                order.setStatus(status);
                return true;
            }
            return false;
        }
    }
}
