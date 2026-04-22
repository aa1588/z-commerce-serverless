package com.zcommerce.shared.model;

import com.zcommerce.shared.dynamodb.KeyBuilder;
import com.zcommerce.shared.dynamodb.converter.InstantConverter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Payment entity for DynamoDB single-table design.
 * 
 * DynamoDB Structure:
 * PK: PAYMENT#{transactionId}
 * SK: TRANSACTION
 */
@DynamoDbBean
public class Payment {
    private String pk;
    private String sk;
    private String entityType;
    private String transactionId;
    private String orderId;
    private BigDecimal amount;
    private PaymentStatus status;
    private PaymentMethod paymentMethod;
    private String paymentDetails;
    private Instant processedAt;

    public enum PaymentStatus {
        PENDING, PROCESSING, COMPLETED, FAILED, REFUNDED
    }

    public enum PaymentMethod {
        CREDIT_CARD, DEBIT_CARD, PAYPAL, BANK_TRANSFER
    }

    public Payment() {
        this.entityType = "PAYMENT";
        this.sk = KeyBuilder.paymentTransactionSK();
        this.status = PaymentStatus.PENDING;
    }

    public Payment(String transactionId, String orderId, BigDecimal amount, PaymentMethod paymentMethod) {
        this();
        this.transactionId = transactionId;
        this.orderId = orderId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.pk = KeyBuilder.paymentPK(transactionId);
        this.processedAt = Instant.now();
    }

    @DynamoDbPartitionKey
    public String getPk() {
        return pk;
    }

    public void setPk(String pk) {
        this.pk = pk;
    }

    @DynamoDbSortKey
    public String getSk() {
        return sk;
    }

    public void setSk(String sk) {
        this.sk = sk;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
        if (transactionId != null) {
            this.pk = KeyBuilder.paymentPK(transactionId);
        }
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getPaymentDetails() {
        return paymentDetails;
    }

    public void setPaymentDetails(String paymentDetails) {
        this.paymentDetails = paymentDetails;
    }

    @DynamoDbConvertedBy(InstantConverter.class)
    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Payment payment = (Payment) o;
        return Objects.equals(transactionId, payment.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId);
    }

    @Override
    public String toString() {
        return "Payment{" +
               "transactionId='" + transactionId + '\'' +
               ", orderId='" + orderId + '\'' +
               ", amount=" + amount +
               ", status=" + status +
               ", paymentMethod=" + paymentMethod +
               ", processedAt=" + processedAt +
               '}';
    }
}