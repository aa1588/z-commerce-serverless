package com.zcommerce.shared.service;

import com.zcommerce.shared.exception.ConflictException;
import com.zcommerce.shared.exception.ResourceNotFoundException;
import com.zcommerce.shared.logging.StructuredLogger;
import com.zcommerce.shared.model.Order;
import com.zcommerce.shared.model.OrderItem;
import com.zcommerce.shared.model.Product;
import com.zcommerce.shared.repository.CartRepository;
import com.zcommerce.shared.repository.OrderRepository;
import com.zcommerce.shared.repository.ProductRepository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Service for handling cross-service integration for order processing.
 * Coordinates between order, cart, and product services.
 */
public class OrderIntegrationService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final StructuredLogger logger;

    public OrderIntegrationService(OrderRepository orderRepository, 
                                 CartRepository cartRepository, 
                                 ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.logger = new StructuredLogger("order-integration-service");
    }

    /**
     * Complete order processing after successful payment.
     * Updates order status to COMPLETED and performs final cleanup.
     */
    public void completeOrder(String orderId) {
        logger.info("order_completion_started", Map.of("orderId", orderId));

        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            throw new ResourceNotFoundException("Order", orderId);
        }

        Order order = orderOpt.get();

        // Validate order can be completed
        if (order.getStatus() != Order.OrderStatus.PROCESSING) {
            throw new ConflictException("Cannot complete order with status: " + order.getStatus());
        }

        // Update order status to completed
        order.setStatus(Order.OrderStatus.COMPLETED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        logger.info("order_completed", Map.of(
            "orderId", orderId,
            "userId", order.getUserId(),
            "totalAmount", order.getTotalAmount()
        ));
    }

    /**
     * Cancel order and restore inventory.
     * Used when payment fails or order is explicitly cancelled.
     */
    public void cancelOrder(String orderId, String reason) {
        logger.info("order_cancellation_started", Map.of(
            "orderId", orderId,
            "reason", reason
        ));

        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            throw new ResourceNotFoundException("Order", orderId);
        }

        Order order = orderOpt.get();

        // Can only cancel pending or processing orders
        if (order.getStatus() != Order.OrderStatus.PENDING && 
            order.getStatus() != Order.OrderStatus.PROCESSING) {
            throw new ConflictException("Cannot cancel order with status: " + order.getStatus());
        }

        // Restore inventory for all items
        for (OrderItem item : order.getItems()) {
            restoreInventory(item.getProductId(), item.getQuantity());
        }

        // Update order status
        order.setStatus(Order.OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        logger.info("order_cancelled", Map.of(
            "orderId", orderId,
            "userId", order.getUserId(),
            "reason", reason
        ));
    }

    /**
     * Mark order as failed and restore inventory.
     * Used when payment processing fails.
     */
    public void failOrder(String orderId, String reason) {
        logger.info("order_failure_started", Map.of(
            "orderId", orderId,
            "reason", reason
        ));

        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            throw new ResourceNotFoundException("Order", orderId);
        }

        Order order = orderOpt.get();

        // Restore inventory for all items
        for (OrderItem item : order.getItems()) {
            restoreInventory(item.getProductId(), item.getQuantity());
        }

        // Update order status
        order.setStatus(Order.OrderStatus.FAILED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        logger.info("order_failed", Map.of(
            "orderId", orderId,
            "userId", order.getUserId(),
            "reason", reason
        ));
    }

    /**
     * Restore inventory for a specific product.
     */
    private void restoreInventory(String productId, int quantity) {
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            int newInventory = product.getInventory() + quantity;
            productRepository.updateInventory(productId, newInventory);
            
            logger.info("inventory_restored", Map.of(
                "productId", productId,
                "quantityRestored", quantity,
                "newInventory", newInventory
            ));
        } else {
            logger.warn("product_not_found_for_inventory_restore", Map.of(
                "productId", productId,
                "quantity", quantity
            ));
        }
    }
}