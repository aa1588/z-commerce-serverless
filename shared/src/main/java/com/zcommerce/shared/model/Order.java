package com.zcommerce.shared.model;

import com.zcommerce.shared.dynamodb.KeyBuilder;
import com.zcommerce.shared.dynamodb.converter.OrderItemListConverter;
import com.zcommerce.shared.dynamodb.converter.InstantConverter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Order entity for DynamoDB single-table design.
 * 
 * DynamoDB Structure:
 * PK: ORDER#{orderId}
 * SK: DETAILS
 * GSI1PK: ORDER#{userId}
 * GSI1SK: {createdAt}
 */
@DynamoDbBean
public class Order {
    private String pk;
    private String sk;
    private String gsi1pk;
    private String gsi1sk;
    private String entityType;
    private String orderId;
    private String userId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private List<OrderItem> items;
    private Instant createdAt;
    private Instant updatedAt;

    public enum OrderStatus {
        PENDING, PROCESSING, COMPLETED, CANCELLED, FAILED
    }

    public Order() {
        this.entityType = "ORDER";
        this.sk = KeyBuilder.orderDetailsSK();
        this.status = OrderStatus.PENDING;
    }

    public Order(String orderId, String userId, List<OrderItem> items, BigDecimal totalAmount) {
        this();
        this.orderId = orderId;
        this.userId = userId;
        this.items = items;
        this.totalAmount = totalAmount;
        this.pk = KeyBuilder.orderPK(orderId);
        this.gsi1pk = KeyBuilder.gsi1PKWithContext("ORDER", userId);
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.gsi1sk = createdAt.toString();
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

    @DynamoDbSecondaryPartitionKey(indexNames = "GSI1")
    public String getGsi1pk() {
        return gsi1pk;
    }

    public void setGsi1pk(String gsi1pk) {
        this.gsi1pk = gsi1pk;
    }

    @DynamoDbSecondarySortKey(indexNames = "GSI1")
    public String getGsi1sk() {
        return gsi1sk;
    }

    public void setGsi1sk(String gsi1sk) {
        this.gsi1sk = gsi1sk;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
        if (orderId != null) {
            this.pk = KeyBuilder.orderPK(orderId);
        }
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
        if (userId != null) {
            this.gsi1pk = KeyBuilder.gsi1PKWithContext("ORDER", userId);
        }
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    @DynamoDbConvertedBy(OrderItemListConverter.class)
    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    @DynamoDbConvertedBy(InstantConverter.class)
    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        if (createdAt != null) {
            this.gsi1sk = createdAt.toString();
        }
    }

    @DynamoDbConvertedBy(InstantConverter.class)
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(orderId, order.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }

    @Override
    public String toString() {
        return "Order{" +
               "orderId='" + orderId + '\'' +
               ", userId='" + userId + '\'' +
               ", status=" + status +
               ", totalAmount=" + totalAmount +
               ", itemCount=" + (items != null ? items.size() : 0) +
               ", createdAt=" + createdAt +
               ", updatedAt=" + updatedAt +
               '}';
    }
}