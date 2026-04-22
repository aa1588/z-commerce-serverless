package com.zcommerce.shared.repository.impl;

import com.zcommerce.shared.dynamodb.KeyBuilder;
import com.zcommerce.shared.model.CartItem;
import com.zcommerce.shared.repository.DynamoDbRepository;
import com.zcommerce.shared.repository.CartRepository;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DynamoDB implementation of CartRepository.
 */
public class DynamoDbCartRepository extends DynamoDbRepository<CartItem> implements CartRepository {

    public DynamoDbCartRepository() {
        super(CartItem.class, "CART_ITEM");
    }

    @Override
    protected Key buildKey(String id) {
        // For cart items, the ID is a composite: userId:productId
        String[] parts = id.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Cart item ID must be in format 'userId:productId'");
        }
        return Key.builder()
            .partitionValue(KeyBuilder.userPK(parts[0]))
            .sortValue(KeyBuilder.cartItemSK(parts[1]))
            .build();
    }

    @Override
    protected String extractId(CartItem cartItem) {
        return cartItem.getUserId() + ":" + cartItem.getProductId();
    }

    @Override
    public List<CartItem> findByUserId(String userId) {
        try {
            List<CartItem> items = findAll().stream()
                .filter(item -> userId.equals(item.getUserId()))
                .collect(Collectors.toList());

            logger.info("cart_find_by_user", Map.of(
                "userId", userId,
                "itemCount", items.size()
            ));

            return items;
        } catch (Exception e) {
            logger.error("cart_find_by_user_error", Map.of(
                "userId", userId,
                "error", e.getMessage()
            ), e);
            throw new RuntimeException("Failed to find cart items for user: " + userId, e);
        }
    }

    @Override
    public Optional<CartItem> findByUserIdAndProductId(String userId, String productId) {
        try {
            Key key = Key.builder()
                .partitionValue(KeyBuilder.userPK(userId))
                .sortValue(KeyBuilder.cartItemSK(productId))
                .build();

            CartItem item = table.getItem(key);

            logger.info("cart_find_by_user_and_product", Map.of(
                "userId", userId,
                "productId", productId,
                "found", item != null
            ));

            return Optional.ofNullable(item);
        } catch (Exception e) {
            logger.error("cart_find_by_user_and_product_error", Map.of(
                "userId", userId,
                "productId", productId,
                "error", e.getMessage()
            ), e);
            throw new RuntimeException("Failed to find cart item for user: " + userId + " and product: " + productId, e);
        }
    }

    @Override
    public int deleteByUserId(String userId) {
        try {
            List<CartItem> items = findByUserId(userId);
            int deletedCount = 0;

            for (CartItem item : items) {
                if (deleteByUserIdAndProductId(userId, item.getProductId())) {
                    deletedCount++;
                }
            }

            logger.info("cart_delete_by_user", Map.of(
                "userId", userId,
                "deletedCount", deletedCount
            ));

            return deletedCount;
        } catch (Exception e) {
            logger.error("cart_delete_by_user_error", Map.of(
                "userId", userId,
                "error", e.getMessage()
            ), e);
            throw new RuntimeException("Failed to delete cart items for user: " + userId, e);
        }
    }

    @Override
    public boolean deleteByUserIdAndProductId(String userId, String productId) {
        try {
            Key key = Key.builder()
                .partitionValue(KeyBuilder.userPK(userId))
                .sortValue(KeyBuilder.cartItemSK(productId))
                .build();

            CartItem deletedItem = table.deleteItem(key);
            boolean deleted = deletedItem != null;

            logger.info("cart_delete_by_user_and_product", Map.of(
                "userId", userId,
                "productId", productId,
                "deleted", deleted
            ));

            return deleted;
        } catch (Exception e) {
            logger.error("cart_delete_by_user_and_product_error", Map.of(
                "userId", userId,
                "productId", productId,
                "error", e.getMessage()
            ), e);
            throw new RuntimeException("Failed to delete cart item for user: " + userId + " and product: " + productId, e);
        }
    }

    @Override
    public int deleteByProductId(String productId) {
        try {
            List<CartItem> items = findAll().stream()
                .filter(item -> productId.equals(item.getProductId()))
                .collect(Collectors.toList());

            int deletedCount = 0;
            for (CartItem item : items) {
                if (deleteByUserIdAndProductId(item.getUserId(), item.getProductId())) {
                    deletedCount++;
                }
            }

            logger.info("cart_delete_by_product", Map.of(
                "productId", productId,
                "deletedCount", deletedCount
            ));

            return deletedCount;
        } catch (Exception e) {
            logger.error("cart_delete_by_product_error", Map.of(
                "productId", productId,
                "error", e.getMessage()
            ), e);
            throw new RuntimeException("Failed to delete cart items for product: " + productId, e);
        }
    }
}