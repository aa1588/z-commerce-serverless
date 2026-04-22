package com.zcommerce.shared.repository.impl;

import com.zcommerce.shared.dynamodb.KeyBuilder;
import com.zcommerce.shared.model.Payment;
import com.zcommerce.shared.repository.DynamoDbRepository;
import com.zcommerce.shared.repository.PaymentRepository;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DynamoDB implementation of PaymentRepository.
 */
public class DynamoDbPaymentRepository extends DynamoDbRepository<Payment> implements PaymentRepository {

    public DynamoDbPaymentRepository() {
        super(Payment.class, "PAYMENT");
    }

    @Override
    protected Key buildKey(String transactionId) {
        return Key.builder()
            .partitionValue(KeyBuilder.paymentPK(transactionId))
            .sortValue(KeyBuilder.paymentTransactionSK())
            .build();
    }

    @Override
    protected String extractId(Payment payment) {
        return payment.getTransactionId();
    }

    @Override
    public Optional<Payment> findByOrderId(String orderId) {
        try {
            Optional<Payment> payment = findAll().stream()
                .filter(p -> orderId.equals(p.getOrderId()))
                .findFirst();

            logger.info("payment_find_by_order", Map.of(
                "orderId", orderId,
                "found", payment.isPresent()
            ));

            return payment;
        } catch (Exception e) {
            logger.error("payment_find_by_order_error", Map.of(
                "orderId", orderId,
                "error", e.getMessage()
            ), e);
            throw new RuntimeException("Failed to find payment for order: " + orderId, e);
        }
    }

    @Override
    public List<Payment> findByStatus(Payment.PaymentStatus status) {
        try {
            List<Payment> payments = findAll().stream()
                .filter(payment -> status.equals(payment.getStatus()))
                .collect(Collectors.toList());

            logger.info("payment_find_by_status", Map.of(
                "status", status.name(),
                "paymentCount", payments.size()
            ));

            return payments;
        } catch (Exception e) {
            logger.error("payment_find_by_status_error", Map.of(
                "status", status.name(),
                "error", e.getMessage()
            ), e);
            throw new RuntimeException("Failed to find payments by status: " + status, e);
        }
    }

    @Override
    public boolean updateStatus(String transactionId, Payment.PaymentStatus status) {
        try {
            Optional<Payment> paymentOpt = findById(transactionId);
            if (paymentOpt.isEmpty()) {
                return false;
            }

            Payment payment = paymentOpt.get();
            payment.setStatus(status);
            payment.setProcessedAt(Instant.now());
            save(payment);

            logger.info("payment_status_updated", Map.of(
                "transactionId", transactionId,
                "newStatus", status.name()
            ));

            return true;
        } catch (Exception e) {
            logger.error("payment_status_update_error", Map.of(
                "transactionId", transactionId,
                "status", status.name(),
                "error", e.getMessage()
            ), e);
            throw new RuntimeException("Failed to update status for payment: " + transactionId, e);
        }
    }
}