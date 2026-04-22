package com.zcommerce.shared.repository.impl;

import com.zcommerce.shared.dynamodb.KeyBuilder;
import com.zcommerce.shared.model.Product;
import com.zcommerce.shared.repository.DynamoDbRepository;
import com.zcommerce.shared.repository.ProductRepository;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DynamoDB implementation of ProductRepository.
 */
public class DynamoDbProductRepository extends DynamoDbRepository<Product> implements ProductRepository {

    public DynamoDbProductRepository() {
        super(Product.class, "PRODUCT");
    }

    @Override
    protected Key buildKey(String productId) {
        return Key.builder()
            .partitionValue(KeyBuilder.productPK(productId))
            .sortValue(KeyBuilder.productDetailsSK())
            .build();
    }

    @Override
    protected String extractId(Product product) {
        return product.getProductId();
    }

    @Override
    public List<Product> findByCategory(String category) {
        try {
            List<Product> products = findAll().stream()
                .filter(product -> category.equals(product.getCategory()))
                .collect(Collectors.toList());

            logger.info("product_find_by_category", Map.of(
                "category", category,
                "count", products.size()
            ));

            return products;
        } catch (Exception e) {
            logger.error("product_find_by_category_error", Map.of(
                "category", category,
                "error", e.getMessage()
            ), e);
            throw new RuntimeException("Failed to find products by category: " + category, e);
        }
    }

    @Override
    public List<Product> findAvailableProducts() {
        try {
            List<Product> products = findAll().stream()
                .filter(Product::isAvailable)
                .collect(Collectors.toList());

            logger.info("product_find_available", Map.of(
                "count", products.size()
            ));

            return products;
        } catch (Exception e) {
            logger.error("product_find_available_error", Map.of(
                "error", e.getMessage()
            ), e);
            throw new RuntimeException("Failed to find available products", e);
        }
    }

    @Override
    public boolean updateInventory(String productId, Integer newInventory) {
        try {
            Optional<Product> productOpt = findById(productId);
            if (productOpt.isEmpty()) {
                return false;
            }

            Product product = productOpt.get();
            product.setInventory(newInventory);
            product.setUpdatedAt(Instant.now());
            save(product);

            logger.info("product_inventory_updated", Map.of(
                "productId", productId,
                "newInventory", newInventory
            ));

            return true;
        } catch (Exception e) {
            logger.error("product_inventory_update_error", Map.of(
                "productId", productId,
                "newInventory", newInventory,
                "error", e.getMessage()
            ), e);
            throw new RuntimeException("Failed to update inventory for product: " + productId, e);
        }
    }

    @Override
    public boolean decreaseInventory(String productId, Integer quantity) {
        try {
            Optional<Product> productOpt = findById(productId);
            if (productOpt.isEmpty()) {
                return false;
            }

            Product product = productOpt.get();
            if (product.getInventory() < quantity) {
                logger.info("product_insufficient_inventory", Map.of(
                    "productId", productId,
                    "requestedQuantity", quantity,
                    "availableInventory", product.getInventory()
                ));
                return false;
            }

            product.setInventory(product.getInventory() - quantity);
            product.setUpdatedAt(Instant.now());
            save(product);

            logger.info("product_inventory_decreased", Map.of(
                "productId", productId,
                "decreasedBy", quantity,
                "newInventory", product.getInventory()
            ));

            return true;
        } catch (Exception e) {
            logger.error("product_inventory_decrease_error", Map.of(
                "productId", productId,
                "quantity", quantity,
                "error", e.getMessage()
            ), e);
            throw new RuntimeException("Failed to decrease inventory for product: " + productId, e);
        }
    }
}