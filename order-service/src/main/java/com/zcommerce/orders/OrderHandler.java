package com.zcommerce.orders;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.zcommerce.shared.api.LambdaHandler;
import com.zcommerce.shared.exception.ConflictException;
import com.zcommerce.shared.exception.ResourceNotFoundException;
import com.zcommerce.shared.exception.ValidationException;
import com.zcommerce.shared.model.CartItem;
import com.zcommerce.shared.model.Order;
import com.zcommerce.shared.model.OrderItem;
import com.zcommerce.shared.model.Product;
import com.zcommerce.shared.repository.CartRepository;
import com.zcommerce.shared.repository.OrderRepository;
import com.zcommerce.shared.repository.ProductRepository;
import com.zcommerce.shared.repository.impl.DynamoDbCartRepository;
import com.zcommerce.shared.repository.impl.DynamoDbOrderRepository;
import com.zcommerce.shared.repository.impl.DynamoDbProductRepository;
import com.zcommerce.shared.service.OrderIntegrationService;
import com.zcommerce.shared.util.ValidationUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lambda handler for order processing operations.
 * Handles order creation, processing, and status management.
 */
public class OrderHandler extends LambdaHandler {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final OrderIntegrationService orderIntegrationService;

    public OrderHandler() {
        this(new DynamoDbOrderRepository(), new DynamoDbCartRepository(), new DynamoDbProductRepository());
    }

    public OrderHandler(OrderRepository orderRepository, CartRepository cartRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.orderIntegrationService = new OrderIntegrationService(orderRepository, cartRepository, productRepository);
    }

    @Override
    protected String getServiceName() {
        return "order-service";
    }

    @Override
    protected APIGatewayProxyResponseEvent processRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();

        logger.info("Processing order service request",
                   Map.of("method", httpMethod, "path", path));

        // Route based on HTTP method and path
        if ("POST".equals(httpMethod) && path.matches(".*/orders$")) {
            return handleCreateOrder(request);
        } else if ("GET".equals(httpMethod) && path.matches(".*/orders/[^/]+$")) {
            return handleGetOrder(request);
        } else if ("GET".equals(httpMethod) && path.matches(".*/users/[^/]+/orders$")) {
            return handleGetUserOrders(request);
        } else if ("PUT".equals(httpMethod) && path.matches(".*/orders/[^/]+/status$")) {
            return handleUpdateOrderStatus(request);
        } else if ("POST".equals(httpMethod) && path.matches(".*/orders/[^/]+/cancel$")) {
            return handleCancelOrder(request);
        }

        throw new ValidationException("Unsupported operation: " + httpMethod + " " + path);
    }

    /**
     * Handle create order from cart
     * POST /orders
     */
    private APIGatewayProxyResponseEvent handleCreateOrder(APIGatewayProxyRequestEvent request) {
        CreateOrderRequest createRequest = parseRequestBody(request.getBody(), CreateOrderRequest.class);

        // Validate input
        ValidationUtils.validateUUID(createRequest.userId, "User ID");

        // Get cart items
        List<CartItem> cartItems = cartRepository.findByUserId(createRequest.userId);
        if (cartItems.isEmpty()) {
            throw new ValidationException("Cart is empty. Cannot create order.");
        }

        // Validate inventory and build order items
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<InventoryReservation> reservations = new ArrayList<>();

        try {
            for (CartItem cartItem : cartItems) {
                Optional<Product> productOpt = productRepository.findById(cartItem.getProductId());
                if (productOpt.isEmpty()) {
                    throw new ResourceNotFoundException("Product", cartItem.getProductId());
                }

                Product product = productOpt.get();

                // Check inventory availability
                if (product.getInventory() < cartItem.getQuantity()) {
                    throw new ConflictException(String.format(
                        "Insufficient inventory for product %s. Available: %d, Requested: %d",
                        product.getName(), product.getInventory(), cartItem.getQuantity()
                    ));
                }

                // Reserve inventory (decrease)
                int originalInventory = product.getInventory();
                if (!productRepository.decreaseInventory(product.getProductId(), cartItem.getQuantity())) {
                    throw new ConflictException("Failed to reserve inventory for product: " + product.getName());
                }
                reservations.add(new InventoryReservation(product.getProductId(), cartItem.getQuantity(), originalInventory));

                // Create order item
                OrderItem orderItem = new OrderItem(
                    product.getProductId(),
                    product.getName(),
                    cartItem.getQuantity(),
                    product.getPrice()
                );
                orderItems.add(orderItem);

                totalAmount = totalAmount.add(product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
            }

            // Create order
            String orderId = UUID.randomUUID().toString();
            Order order = new Order(orderId, createRequest.userId, orderItems, totalAmount);
            order.setStatus(Order.OrderStatus.PENDING);
            orderRepository.save(order);

            // Clear cart
            cartRepository.deleteByUserId(createRequest.userId);

            logger.info("order_created", Map.of(
                "orderId", orderId,
                "userId", createRequest.userId,
                "itemCount", orderItems.size(),
                "totalAmount", totalAmount
            ));

            return createSuccessResponse(
                toOrderResponse(order),
                "Order created successfully"
            );

        } catch (Exception e) {
            // Rollback inventory reservations on failure
            for (InventoryReservation reservation : reservations) {
                try {
                    productRepository.updateInventory(reservation.productId, reservation.originalInventory);
                    logger.info("inventory_rollback", Map.of(
                        "productId", reservation.productId,
                        "restoredInventory", reservation.originalInventory
                    ));
                } catch (Exception rollbackException) {
                    logger.error("inventory_rollback_failed", Map.of(
                        "productId", reservation.productId,
                        "error", rollbackException.getMessage()
                    ), rollbackException);
                }
            }
            throw e;
        }
    }

    /**
     * Handle get order details
     * GET /orders/{orderId}
     */
    private APIGatewayProxyResponseEvent handleGetOrder(APIGatewayProxyRequestEvent request) {
        String orderId = getPathParameter(request, "orderId");
        ValidationUtils.validateUUID(orderId, "Order ID");

        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            throw new ResourceNotFoundException("Order", orderId);
        }

        Order order = orderOpt.get();

        logger.info("order_retrieved", Map.of(
            "orderId", orderId
        ));

        return createSuccessResponse(
            toOrderResponse(order),
            "Order retrieved successfully"
        );
    }

    /**
     * Handle get user's orders
     * GET /users/{userId}/orders
     */
    private APIGatewayProxyResponseEvent handleGetUserOrders(APIGatewayProxyRequestEvent request) {
        String userId = getPathParameter(request, "userId");
        ValidationUtils.validateUUID(userId, "User ID");

        List<Order> orders = orderRepository.findByUserId(userId);

        logger.info("user_orders_retrieved", Map.of(
            "userId", userId,
            "orderCount", orders.size()
        ));

        List<Map<String, Object>> ordersList = orders.stream()
            .map(this::toOrderResponse)
            .collect(Collectors.toList());

        return createSuccessResponse(
            Map.of(
                "userId", userId,
                "orders", ordersList,
                "count", orders.size()
            ),
            "User orders retrieved successfully"
        );
    }

    /**
     * Handle update order status
     * PUT /orders/{orderId}/status
     */
    private APIGatewayProxyResponseEvent handleUpdateOrderStatus(APIGatewayProxyRequestEvent request) {
        String orderId = getPathParameter(request, "orderId");
        ValidationUtils.validateUUID(orderId, "Order ID");

        UpdateStatusRequest updateRequest = parseRequestBody(request.getBody(), UpdateStatusRequest.class);
        ValidationUtils.validateRequired(updateRequest.status, "Status");

        Order.OrderStatus newStatus;
        try {
            newStatus = Order.OrderStatus.valueOf(updateRequest.status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid status: " + updateRequest.status);
        }

        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            throw new ResourceNotFoundException("Order", orderId);
        }

        Order order = orderOpt.get();
        order.setStatus(newStatus);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        logger.info("order_status_updated", Map.of(
            "orderId", orderId,
            "newStatus", newStatus
        ));

        return createSuccessResponse(
            toOrderResponse(order),
            "Order status updated successfully"
        );
    }

    /**
     * Handle cancel order
     * POST /orders/{orderId}/cancel
     */
    private APIGatewayProxyResponseEvent handleCancelOrder(APIGatewayProxyRequestEvent request) {
        String orderId = getPathParameter(request, "orderId");
        ValidationUtils.validateUUID(orderId, "Order ID");

        // Use integration service for proper cancellation
        orderIntegrationService.cancelOrder(orderId, "User requested cancellation");

        // Get updated order
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            throw new ResourceNotFoundException("Order", orderId);
        }

        Order order = orderOpt.get();

        logger.info("order_cancelled", Map.of(
            "orderId", orderId
        ));

        return createSuccessResponse(
            toOrderResponse(order),
            "Order cancelled successfully"
        );
    }

    /**
     * Convert Order to response map
     */
    private Map<String, Object> toOrderResponse(Order order) {
        List<Map<String, Object>> itemsList = new ArrayList<>();
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("productId", item.getProductId());
                itemMap.put("productName", item.getProductName());
                itemMap.put("quantity", item.getQuantity());
                itemMap.put("price", item.getPrice());
                itemMap.put("itemTotal", item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                itemsList.add(itemMap);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("orderId", order.getOrderId());
        response.put("userId", order.getUserId());
        response.put("status", order.getStatus().name());
        response.put("items", itemsList);
        response.put("totalAmount", order.getTotalAmount());
        response.put("createdAt", order.getCreatedAt().toString());
        response.put("updatedAt", order.getUpdatedAt().toString());

        return response;
    }

    // Helper class for tracking inventory reservations during order creation
    private static class InventoryReservation {
        final String productId;
        final int quantityReserved;
        final int originalInventory;

        InventoryReservation(String productId, int quantityReserved, int originalInventory) {
            this.productId = productId;
            this.quantityReserved = quantityReserved;
            this.originalInventory = originalInventory;
        }
    }

    // Request DTOs
    public static class CreateOrderRequest {
        public String userId;
    }

    public static class UpdateStatusRequest {
        public String status;
    }
}
