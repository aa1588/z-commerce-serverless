package com.zcommerce.shared.repository.impl;

import com.zcommerce.shared.dynamodb.KeyBuilder;
import com.zcommerce.shared.model.Order;
import com.zcommerce.shared.repository.DynamoDbRepository;
import com.zcommerce.shared.repository.OrderRepository;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DynamoDB implementation of OrderRepository.
 */
public class DynamoDbOrderRepository extends DynamoDbRepository<Order> implements OrderRepository {

    public DynamoDbOrderRepository() {
        super(Order.class, "ORDER");
    }

    @Override
    protected Key buildKey(String orderId) {
        return Key.builder()
            .partitionValue(KeyBuilder.orderPK(orderId))
            .sortValue(KeyBuilder.orderDetailsSK())
            .build();
    }

    @Override
    protected String extractId(Order order) {
        return order.getOrderId();
    }

    @Override
    public List<Order> findByUserId(String userId) {
        try {
            List<Order> orders = findAll().stream()
                .filter(order -> userId.equals(order.getUserId()))
                .sorted((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt())) // Newest first
                .collect(Collectors.toList());

            logger.info("order_find_by_user", Map.of(
                "userId", userId,
                "orderCount", orders.size()
            ));

            return orders;
        } catch (Exception e) {
            logger.error("order_find_by_user_error", Map.of(
                "userId", userId,
                "error", e.getMessage()
            ), e);
            throw new RuntimeException("Failed to find orders for user: " + userId, e);
        }
    }

    @Override
    public List<Order> findByStatus(Order.OrderStatus status) {
        try {
            List<Order> orders = findAll().stream()
                .filter(order -> status.equals(order.getStatus()))
                .collect(Collectors.toList());

            logger.info("order_find_by_status", Map.of(
                "status", status.name(),
                "orderCount", orders.size()
            ));

            return orders;
        } catch (Exception e) {
            logger.error("order_find_by_status_error", Map.of(
                "status", status.name(),
                "error", e.getMessage()
            ), e);
            throw new RuntimeException("Failed to find orders by status: " + status, e);
        }
    }

    @Override
    public boolean updateStatus(String orderId, Order.OrderStatus status) {
        try {
            Optional<Order> orderOpt = findById(orderId);
            if (orderOpt.isEmpty()) {
                return false;
            }

            Order order = orderOpt.get();
            order.setStatus(status);
            order.setUpdatedAt(Instant.now());
            save(order);

            logger.info("order_status_updated", Map.of(
                "orderId", orderId,
                "newStatus", status.name()
            ));

            return true;
        } catch (Exception e) {
            logger.error("order_status_update_error", Map.of(
                "orderId", orderId,
                "status", status.name(),
                "error", e.getMessage()
            ), e);
            throw new RuntimeException("Failed to update status for order: " + orderId, e);
        }
    }
}