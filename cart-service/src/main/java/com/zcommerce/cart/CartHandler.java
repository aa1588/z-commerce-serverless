package com.zcommerce.cart;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.zcommerce.shared.api.LambdaHandler;
import com.zcommerce.shared.exception.ConflictException;
import com.zcommerce.shared.exception.ResourceNotFoundException;
import com.zcommerce.shared.exception.ValidationException;
import com.zcommerce.shared.model.CartItem;
import com.zcommerce.shared.model.Product;
import com.zcommerce.shared.repository.CartRepository;
import com.zcommerce.shared.repository.ProductRepository;
import com.zcommerce.shared.repository.impl.DynamoDbCartRepository;
import com.zcommerce.shared.repository.impl.DynamoDbProductRepository;
import com.zcommerce.shared.util.ValidationUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Lambda handler for shopping cart operations.
 * Handles cart item management and total calculations.
 */
public class CartHandler extends LambdaHandler {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    public CartHandler() {
        this(new DynamoDbCartRepository(), new DynamoDbProductRepository());
    }

    public CartHandler(CartRepository cartRepository, ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
    }

    @Override
    protected String getServiceName() {
        return "cart-service";
    }

    @Override
    protected APIGatewayProxyResponseEvent processRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();

        logger.info("Processing cart service request",
                   Map.of("method", httpMethod, "path", path));

        // Route based on HTTP method and path
        if ("GET".equals(httpMethod) && path.matches(".*/cart/[^/]+$")) {
            return handleGetCart(request);
        } else if ("POST".equals(httpMethod) && path.matches(".*/cart/[^/]+/items$")) {
            return handleAddItem(request);
        } else if ("PUT".equals(httpMethod) && path.matches(".*/cart/[^/]+/items/[^/]+$")) {
            return handleUpdateItem(request);
        } else if ("DELETE".equals(httpMethod) && path.matches(".*/cart/[^/]+/items/[^/]+$")) {
            return handleRemoveItem(request);
        } else if ("DELETE".equals(httpMethod) && path.matches(".*/cart/[^/]+$")) {
            return handleClearCart(request);
        }

        throw new ValidationException("Unsupported operation: " + httpMethod + " " + path);
    }

    /**
     * Handle get user's cart
     * GET /cart/{userId}
     */
    private APIGatewayProxyResponseEvent handleGetCart(APIGatewayProxyRequestEvent request) {
        String userId = getPathParameter(request, "userId");
        ValidationUtils.validateUUID(userId, "User ID");

        List<CartItem> cartItems = cartRepository.findByUserId(userId);

        // Get product details and calculate total
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<Map<String, Object>> itemsList = new java.util.ArrayList<>();

        for (CartItem item : cartItems) {
            Optional<Product> productOpt = productRepository.findById(item.getProductId());
            if (productOpt.isPresent()) {
                Product product = productOpt.get();
                BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                totalAmount = totalAmount.add(itemTotal);

                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("productId", item.getProductId());
                itemMap.put("productName", product.getName());
                itemMap.put("quantity", item.getQuantity());
                itemMap.put("unitPrice", product.getPrice());
                itemMap.put("itemTotal", itemTotal);
                itemMap.put("available", product.isAvailable());
                itemMap.put("inStock", product.getInventory() >= item.getQuantity());
                itemMap.put("addedAt", item.getAddedAt().toString());
                itemsList.add(itemMap);
            }
        }

        logger.info("cart_retrieved", Map.of(
            "userId", userId,
            "itemCount", cartItems.size(),
            "totalAmount", totalAmount
        ));

        return createSuccessResponse(
            Map.of(
                "userId", userId,
                "items", itemsList,
                "itemCount", itemsList.size(),
                "totalAmount", totalAmount
            ),
            "Cart retrieved successfully"
        );
    }

    /**
     * Handle add item to cart
     * POST /cart/{userId}/items
     */
    private APIGatewayProxyResponseEvent handleAddItem(APIGatewayProxyRequestEvent request) {
        String userId = getPathParameter(request, "userId");
        ValidationUtils.validateUUID(userId, "User ID");

        AddItemRequest addRequest = parseRequestBody(request.getBody(), AddItemRequest.class);

        // Validate input
        ValidationUtils.validateRequired(addRequest.productId, "Product ID");
        ValidationUtils.validatePositive(addRequest.quantity, "Quantity");

        // Check if product exists
        Optional<Product> productOpt = productRepository.findById(addRequest.productId);
        if (productOpt.isEmpty()) {
            throw new ResourceNotFoundException("Product", addRequest.productId);
        }

        Product product = productOpt.get();

        // Check inventory
        if (product.getInventory() < addRequest.quantity) {
            throw new ConflictException(String.format(
                "Insufficient inventory for product %s. Available: %d, Requested: %d",
                addRequest.productId, product.getInventory(), addRequest.quantity
            ));
        }

        // Check if item already in cart
        Optional<CartItem> existingItem = cartRepository.findByUserIdAndProductId(userId, addRequest.productId);

        CartItem cartItem;
        if (existingItem.isPresent()) {
            // Update quantity
            cartItem = existingItem.get();
            int newQuantity = cartItem.getQuantity() + addRequest.quantity;

            // Check inventory for new total quantity
            if (product.getInventory() < newQuantity) {
                throw new ConflictException(String.format(
                    "Insufficient inventory for product %s. Available: %d, Requested total: %d",
                    addRequest.productId, product.getInventory(), newQuantity
                ));
            }

            cartItem.setQuantity(newQuantity);
        } else {
            // Create new cart item
            cartItem = new CartItem(userId, addRequest.productId, addRequest.quantity);
        }

        cartRepository.save(cartItem);

        logger.info("cart_item_added", Map.of(
            "userId", userId,
            "productId", addRequest.productId,
            "quantity", cartItem.getQuantity()
        ));

        BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));

        return createSuccessResponse(
            Map.of(
                "userId", userId,
                "productId", cartItem.getProductId(),
                "productName", product.getName(),
                "quantity", cartItem.getQuantity(),
                "unitPrice", product.getPrice(),
                "itemTotal", itemTotal
            ),
            "Item added to cart successfully"
        );
    }

    /**
     * Handle update item quantity
     * PUT /cart/{userId}/items/{productId}
     */
    private APIGatewayProxyResponseEvent handleUpdateItem(APIGatewayProxyRequestEvent request) {
        String userId = getPathParameter(request, "userId");
        String productId = getPathParameter(request, "productId");

        ValidationUtils.validateUUID(userId, "User ID");

        UpdateItemRequest updateRequest = parseRequestBody(request.getBody(), UpdateItemRequest.class);
        ValidationUtils.validatePositive(updateRequest.quantity, "Quantity");

        // Check if item exists in cart
        Optional<CartItem> cartItemOpt = cartRepository.findByUserIdAndProductId(userId, productId);
        if (cartItemOpt.isEmpty()) {
            throw new ResourceNotFoundException("Cart item", productId);
        }

        // Check if product exists
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            throw new ResourceNotFoundException("Product", productId);
        }

        Product product = productOpt.get();

        // Check inventory
        if (product.getInventory() < updateRequest.quantity) {
            throw new ConflictException(String.format(
                "Insufficient inventory for product %s. Available: %d, Requested: %d",
                productId, product.getInventory(), updateRequest.quantity
            ));
        }

        CartItem cartItem = cartItemOpt.get();
        cartItem.setQuantity(updateRequest.quantity);
        cartRepository.save(cartItem);

        logger.info("cart_item_updated", Map.of(
            "userId", userId,
            "productId", productId,
            "newQuantity", updateRequest.quantity
        ));

        BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));

        return createSuccessResponse(
            Map.of(
                "userId", userId,
                "productId", productId,
                "productName", product.getName(),
                "quantity", cartItem.getQuantity(),
                "unitPrice", product.getPrice(),
                "itemTotal", itemTotal
            ),
            "Cart item updated successfully"
        );
    }

    /**
     * Handle remove item from cart
     * DELETE /cart/{userId}/items/{productId}
     */
    private APIGatewayProxyResponseEvent handleRemoveItem(APIGatewayProxyRequestEvent request) {
        String userId = getPathParameter(request, "userId");
        String productId = getPathParameter(request, "productId");

        ValidationUtils.validateUUID(userId, "User ID");

        // Check if item exists in cart
        Optional<CartItem> cartItemOpt = cartRepository.findByUserIdAndProductId(userId, productId);
        if (cartItemOpt.isEmpty()) {
            throw new ResourceNotFoundException("Cart item", productId);
        }

        cartRepository.deleteByUserIdAndProductId(userId, productId);

        logger.info("cart_item_removed", Map.of(
            "userId", userId,
            "productId", productId
        ));

        return createSuccessResponse(
            Map.of(
                "userId", userId,
                "productId", productId,
                "removed", true
            ),
            "Item removed from cart successfully"
        );
    }

    /**
     * Handle clear entire cart
     * DELETE /cart/{userId}
     */
    private APIGatewayProxyResponseEvent handleClearCart(APIGatewayProxyRequestEvent request) {
        String userId = getPathParameter(request, "userId");
        ValidationUtils.validateUUID(userId, "User ID");

        int deletedCount = cartRepository.deleteByUserId(userId);

        logger.info("cart_cleared", Map.of(
            "userId", userId,
            "itemsRemoved", deletedCount
        ));

        return createSuccessResponse(
            Map.of(
                "userId", userId,
                "itemsRemoved", deletedCount,
                "cleared", true
            ),
            "Cart cleared successfully"
        );
    }

    // Request DTOs
    public static class AddItemRequest {
        public String productId;
        public Integer quantity;
    }

    public static class UpdateItemRequest {
        public Integer quantity;
    }
}
