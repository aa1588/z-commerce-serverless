package com.zcommerce.shared.model;

import com.zcommerce.shared.dynamodb.KeyBuilder;
import com.zcommerce.shared.dynamodb.converter.InstantConverter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Product entity for DynamoDB single-table design.
 * 
 * DynamoDB Structure:
 * PK: PRODUCT#{productId}
 * SK: DETAILS
 * GSI1PK: PRODUCT
 * GSI1SK: {createdAt}
 */
@DynamoDbBean
public class Product {
    private String pk;
    private String sk;
    private String gsi1pk;
    private String gsi1sk;
    private String entityType;
    private String productId;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer inventory;
    private String category;
    private Instant createdAt;
    private Instant updatedAt;

    public Product() {
        this.entityType = "PRODUCT";
        this.sk = KeyBuilder.productDetailsSK();
        this.gsi1pk = "PRODUCT";
    }

    public Product(String productId, String name, String description, BigDecimal price, Integer inventory, String category) {
        this();
        this.productId = productId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.inventory = inventory;
        this.category = category;
        this.pk = KeyBuilder.productPK(productId);
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

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
        if (productId != null) {
            this.pk = KeyBuilder.productPK(productId);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getInventory() {
        return inventory;
    }

    public void setInventory(Integer inventory) {
        this.inventory = inventory;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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

    public boolean isAvailable() {
        return inventory != null && inventory > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return Objects.equals(productId, product.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId);
    }

    @Override
    public String toString() {
        return "Product{" +
               "productId='" + productId + '\'' +
               ", name='" + name + '\'' +
               ", price=" + price +
               ", inventory=" + inventory +
               ", category='" + category + '\'' +
               ", createdAt=" + createdAt +
               ", updatedAt=" + updatedAt +
               '}';
    }
}