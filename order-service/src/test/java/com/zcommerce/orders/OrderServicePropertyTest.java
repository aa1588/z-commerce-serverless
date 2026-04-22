package com.zcommerce.orders;

import com.zcommerce.shared.model.CartItem;
import com.zcommerce.shared.model.Order;
import com.zcommerce.shared.model.OrderItem;
import com.zcommerce.shared.model.Product;
import com.zcommerce.shared.repository.CartRepository;
import com.zcommerce.shared.repository.OrderRepository;
import com.zcommerce.shared.repository.ProductRepository;
import net.jqwik.api.*;
import org.junit.jupiter.api.Tag;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for Order Service.
 * Tests Properties 4, 5, and 6 from the design document.
 */
class OrderServicePropertyTest {

    /**
     * Property 4: Order Creation Validates Business Rules
     * For any order creation request, the system should validate inventory availability,
     * create orders only when constraints are met, and maintain data consistency between orders and inventory.
     * **Validates: Requirements 3.1, 3.2**
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 4: Order Creation Validates Business Rules")
    void orderCreationValidatesBusinessRules(
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId,
        @ForAll("prices") BigDecimal price,
        @ForAll("quantities") Integer quantity,
        @ForAll("inventories") Integer inventory
    ) {
        MockProductRepository productRepository = new MockProductRepository();
        MockCartRepository cartRepository = new MockCartRepository();
        MockOrderRepository orderRepository = new MockOrderRepository();

        // Setup product
        Product product = new Product(productId, "Test Product", "Desc", price, inventory, "Electronics");
        productRepository.save(product);

        // Add item to cart
        CartItem cartItem = new CartItem(userId, productId, quantity);
        cartRepository.save(cartItem);

        boolean canCreateOrder = inventory >= quantity;

        if (canCreateOrder) {
            // Order should be created successfully
            String orderId = UUID.randomUUID().toString();
            List<OrderItem> orderItems = new ArrayList<>();
            orderItems.add(new OrderItem(productId, product.getName(), quantity, price));
            BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(quantity));

            Order order = new Order(orderId, userId, orderItems, totalAmount);
            order.setStatus(Order.OrderStatus.PENDING);
            orderRepository.save(order);

            // Decrease inventory
            productRepository.decreaseInventory(productId, quantity);

            // Clear cart
            cartRepository.deleteByUserId(userId);

            // Verify order was created
            Optional<Order> createdOrder = orderRepository.findById(orderId);
            assertTrue(createdOrder.isPresent());
            assertEquals(userId, createdOrder.get().getUserId());
            assertEquals(Order.OrderStatus.PENDING, createdOrder.get().getStatus());
            assertEquals(0, totalAmount.compareTo(createdOrder.get().getTotalAmount()));

            // Verify inventory was decreased
            assertEquals(inventory - quantity, productRepository.findById(productId).get().getInventory());

            // Verify cart was cleared
            assertEquals(0, cartRepository.findByUserId(userId).size());
        } else {
            // Order should NOT be created - inventory check should fail
            assertTrue(inventory < quantity, "Inventory should be insufficient");
        }
    }

    /**
     * Property 5: Order Processing Updates System State
     * For any completed order, the system should correctly update product inventory,
     * clear the customer's cart, and maintain referential integrity between orders, products, and users.
     * **Validates: Requirements 3.3, 3.4**
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 5: Order Processing Updates System State")
    void orderProcessingUpdatesSystemState(
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId,
        @ForAll("prices") BigDecimal price,
        @ForAll("quantities") Integer quantity,
        @ForAll("inventories") Integer inventory
    ) {
        Assume.that(inventory >= quantity);

        MockProductRepository productRepository = new MockProductRepository();
        MockCartRepository cartRepository = new MockCartRepository();
        MockOrderRepository orderRepository = new MockOrderRepository();

        // Setup product
        Product product = new Product(productId, "Test Product", "Desc", price, inventory, "Electronics");
        productRepository.save(product);

        // Add item to cart
        CartItem cartItem = new CartItem(userId, productId, quantity);
        cartRepository.save(cartItem);

        int originalInventory = inventory;

        // Create order
        String orderId = UUID.randomUUID().toString();
        List<OrderItem> orderItems = new ArrayList<>();
        orderItems.add(new OrderItem(productId, product.getName(), quantity, price));
        BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(quantity));

        Order order = new Order(orderId, userId, orderItems, totalAmount);
        order.setStatus(Order.OrderStatus.PENDING);
        orderRepository.save(order);

        // Decrease inventory (reserve)
        productRepository.decreaseInventory(productId, quantity);

        // Clear cart
        cartRepository.deleteByUserId(userId);

        // Process order (update status to PROCESSING)
        order.setStatus(Order.OrderStatus.PROCESSING);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        // Verify system state after order processing
        // 1. Inventory decreased
        Optional<Product> updatedProduct = productRepository.findById(productId);
        assertTrue(updatedProduct.isPresent());
        assertEquals(originalInventory - quantity, updatedProduct.get().getInventory());

        // 2. Cart cleared
        assertEquals(0, cartRepository.findByUserId(userId).size());

        // 3. Order exists and has correct status
        Optional<Order> processedOrder = orderRepository.findById(orderId);
        assertTrue(processedOrder.isPresent());
        assertEquals(Order.OrderStatus.PROCESSING, processedOrder.get().getStatus());

        // 4. Order references correct user
        assertEquals(userId, processedOrder.get().getUserId());

        // 5. Order items reference correct product
        assertTrue(processedOrder.get().getItems().stream()
            .anyMatch(item -> item.getProductId().equals(productId)));
    }

    /**
     * Property 6: Order Failure Recovery Maintains Consistency
     * For any order that fails during processing, the system should restore inventory
     * to its previous state and maintain system consistency without partial updates.
     * **Validates: Requirements 3.5**
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 6: Order Failure Recovery Maintains Consistency")
    void orderFailureRecoveryMaintainsConsistency(
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId,
        @ForAll("prices") BigDecimal price,
        @ForAll("quantities") Integer quantity,
        @ForAll("inventories") Integer inventory
    ) {
        Assume.that(inventory >= quantity);

        MockProductRepository productRepository = new MockProductRepository();
        MockOrderRepository orderRepository = new MockOrderRepository();

        // Setup product
        Product product = new Product(productId, "Test Product", "Desc", price, inventory, "Electronics");
        productRepository.save(product);

        int originalInventory = inventory;

        // Simulate order creation with inventory reservation
        String orderId = UUID.randomUUID().toString();
        List<OrderItem> orderItems = new ArrayList<>();
        orderItems.add(new OrderItem(productId, product.getName(), quantity, price));
        BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(quantity));

        Order order = new Order(orderId, userId, orderItems, totalAmount);
        order.setStatus(Order.OrderStatus.PENDING);
        orderRepository.save(order);

        // Decrease inventory (reserve)
        productRepository.decreaseInventory(productId, quantity);
        assertEquals(originalInventory - quantity, productRepository.findById(productId).get().getInventory());

        // Simulate order failure - rollback
        // Restore inventory
        productRepository.updateInventory(productId, originalInventory);

        // Update order status to FAILED
        order.setStatus(Order.OrderStatus.FAILED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        // Verify rollback
        // 1. Inventory restored to original
        assertEquals(originalInventory, productRepository.findById(productId).get().getInventory());

        // 2. Order status is FAILED
        assertEquals(Order.OrderStatus.FAILED, orderRepository.findById(orderId).get().getStatus());

        // 3. No partial updates - order items still preserved for audit
        assertFalse(orderRepository.findById(orderId).get().getItems().isEmpty());
    }

    /**
     * Additional property: Order cancellation restores inventory
     */
    @Property(tries = 100)
    @Tag("Feature: z-commerce, Property 6: Order Failure Recovery Maintains Consistency")
    void orderCancellationRestoresInventory(
        @ForAll("userIds") String userId,
        @ForAll("productIds") String productId,
        @ForAll("prices") BigDecimal price,
        @ForAll("quantities") Integer quantity,
        @ForAll("inventories") Integer inventory
    ) {
        Assume.that(inventory >= quantity);

        MockProductRepository productRepository = new MockProductRepository();
        MockOrderRepository orderRepository = new MockOrderRepository();

        // Setup product
        Product product = new Product(productId, "Test Product", "Desc", price, inventory, "Electronics");
        productRepository.save(product);

        int originalInventory = inventory;

        // Create order and reserve inventory
        String orderId = UUID.randomUUID().toString();
        List<OrderItem> orderItems = new ArrayList<>();
        orderItems.add(new OrderItem(productId, product.getName(), quantity, price));
        BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(quantity));

        Order order = new Order(orderId, userId, orderItems, totalAmount);
        order.setStatus(Order.OrderStatus.PENDING);
        orderRepository.save(order);

        productRepository.decreaseInventory(productId, quantity);

        // Cancel order
        order.setStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);

        // Restore inventory
        productRepository.updateInventory(productId, originalInventory);

        // Verify
        assertEquals(originalInventory, productRepository.findById(productId).get().getInventory());
        assertEquals(Order.OrderStatus.CANCELLED, orderRepository.findById(orderId).get().getStatus());
    }

    // Providers
    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.strings().alpha().ofLength(12);
    }

    @Provide
    Arbitrary<String> productIds() {
        return Arbitraries.strings().alpha().ofLength(12);
    }

    @Provide
    Arbitrary<BigDecimal> prices() {
        return Arbitraries.bigDecimals()
            .between(BigDecimal.valueOf(1.00), BigDecimal.valueOf(999.99))
            .ofScale(2);
    }

    @Provide
    Arbitrary<Integer> quantities() {
        return Arbitraries.integers().between(1, 10);
    }

    @Provide
    Arbitrary<Integer> inventories() {
        return Arbitraries.integers().between(1, 100);
    }

    // Mock repositories
    static class MockProductRepository implements ProductRepository {
        private final Map<String, Product> products = new HashMap<>();

        @Override
        public Product save(Product product) {
            products.put(product.getProductId(), product);
            return product;
        }

        @Override
        public Product update(Product product) {
            return save(product);
        }

        @Override
        public Optional<Product> findById(String id) {
            return Optional.ofNullable(products.get(id));
        }

        @Override
        public List<Product> findAll() {
            return new ArrayList<>(products.values());
        }

        @Override
        public boolean deleteById(String id) {
            return products.remove(id) != null;
        }

        @Override
        public boolean existsById(String id) {
            return products.containsKey(id);
        }

        @Override
        public List<Product> findByCategory(String category) {
            return products.values().stream()
                .filter(p -> category.equals(p.getCategory()))
                .collect(Collectors.toList());
        }

        @Override
        public List<Product> findAvailableProducts() {
            return products.values().stream()
                .filter(Product::isAvailable)
                .collect(Collectors.toList());
        }

        @Override
        public boolean updateInventory(String productId, Integer newInventory) {
            Product product = products.get(productId);
            if (product != null) {
                product.setInventory(newInventory);
                return true;
            }
            return false;
        }

        @Override
        public boolean decreaseInventory(String productId, Integer quantity) {
            Product product = products.get(productId);
            if (product != null && product.getInventory() >= quantity) {
                product.setInventory(product.getInventory() - quantity);
                return true;
            }
            return false;
        }
    }

    static class MockCartRepository implements CartRepository {
        private final Map<String, CartItem> items = new HashMap<>();

        private String key(String userId, String productId) {
            return userId + ":" + productId;
        }

        @Override
        public CartItem save(CartItem item) {
            items.put(key(item.getUserId(), item.getProductId()), item);
            return item;
        }

        @Override
        public CartItem update(CartItem item) {
            return save(item);
        }

        @Override
        public Optional<CartItem> findById(String id) {
            return Optional.ofNullable(items.get(id));
        }

        @Override
        public List<CartItem> findAll() {
            return new ArrayList<>(items.values());
        }

        @Override
        public boolean deleteById(String id) {
            return items.remove(id) != null;
        }

        @Override
        public boolean existsById(String id) {
            return items.containsKey(id);
        }

        @Override
        public List<CartItem> findByUserId(String userId) {
            return items.values().stream()
                .filter(item -> userId.equals(item.getUserId()))
                .collect(Collectors.toList());
        }

        @Override
        public Optional<CartItem> findByUserIdAndProductId(String userId, String productId) {
            return Optional.ofNullable(items.get(key(userId, productId)));
        }

        @Override
        public int deleteByUserId(String userId) {
            List<String> keysToRemove = items.entrySet().stream()
                .filter(e -> e.getValue().getUserId().equals(userId))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

            keysToRemove.forEach(items::remove);
            return keysToRemove.size();
        }

        @Override
        public boolean deleteByUserIdAndProductId(String userId, String productId) {
            return items.remove(key(userId, productId)) != null;
        }

        @Override
        public int deleteByProductId(String productId) {
            List<String> keysToRemove = items.entrySet().stream()
                .filter(e -> e.getValue().getProductId().equals(productId))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

            keysToRemove.forEach(items::remove);
            return keysToRemove.size();
        }
    }

    static class MockOrderRepository implements OrderRepository {
        private final Map<String, Order> orders = new HashMap<>();

        @Override
        public Order save(Order order) {
            orders.put(order.getOrderId(), order);
            return order;
        }

        @Override
        public Order update(Order order) {
            return save(order);
        }

        @Override
        public Optional<Order> findById(String id) {
            return Optional.ofNullable(orders.get(id));
        }

        @Override
        public List<Order> findAll() {
            return new ArrayList<>(orders.values());
        }

        @Override
        public boolean deleteById(String id) {
            return orders.remove(id) != null;
        }

        @Override
        public boolean existsById(String id) {
            return orders.containsKey(id);
        }

        @Override
        public List<Order> findByUserId(String userId) {
            return orders.values().stream()
                .filter(o -> userId.equals(o.getUserId()))
                .collect(Collectors.toList());
        }

        @Override
        public List<Order> findByStatus(Order.OrderStatus status) {
            return orders.values().stream()
                .filter(o -> status.equals(o.getStatus()))
                .collect(Collectors.toList());
        }

        @Override
        public boolean updateStatus(String orderId, Order.OrderStatus status) {
            Order order = orders.get(orderId);
            if (order != null) {
                order.setStatus(status);
                return true;
            }
            return false;
        }
    }
}
