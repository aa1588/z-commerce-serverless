package com.zcommerce.shared.model;

import com.zcommerce.shared.dynamodb.KeyBuilder;
import com.zcommerce.shared.dynamodb.converter.InstantConverter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;

import java.time.Instant;
import java.util.Objects;

/**
 * CartItem entity for DynamoDB single-table design.
 * 
 * DynamoDB Structure:
 * PK: USER#{userId}
 * SK: CART#PRODUCT#{productId}
 */
@DynamoDbBean
public class CartItem {
    private String pk;
    private String sk;
    private String entityType;
    private String userId;
    private String productId;
    private Integer quantity;
    private Instant addedAt;

    public CartItem() {
        this.entityType = "CART_ITEM";
    }

    public CartItem(String userId, String productId, Integer quantity) {
        this();
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.pk = KeyBuilder.userPK(userId);
        this.sk = KeyBuilder.cartItemSK(productId);
        this.addedAt = Instant.now();
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
        if (userId != null) {
            this.pk = KeyBuilder.userPK(userId);
        }
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
        if (productId != null && userId != null) {
            this.sk = KeyBuilder.cartItemSK(productId);
        }
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    @DynamoDbConvertedBy(InstantConverter.class)
    public Instant getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(Instant addedAt) {
        this.addedAt = addedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CartItem cartItem = (CartItem) o;
        return Objects.equals(userId, cartItem.userId) &&
               Objects.equals(productId, cartItem.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, productId);
    }

    @Override
    public String toString() {
        return "CartItem{" +
               "userId='" + userId + '\'' +
               ", productId='" + productId + '\'' +
               ", quantity=" + quantity +
               ", addedAt=" + addedAt +
               '}';
    }
}