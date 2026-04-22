package com.zcommerce.payments;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.zcommerce.shared.api.LambdaHandler;
import com.zcommerce.shared.exception.ConflictException;
import com.zcommerce.shared.exception.ResourceNotFoundException;
import com.zcommerce.shared.exception.ValidationException;
import com.zcommerce.shared.model.Order;
import com.zcommerce.shared.model.Payment;
import com.zcommerce.shared.repository.CartRepository;
import com.zcommerce.shared.repository.OrderRepository;
import com.zcommerce.shared.repository.PaymentRepository;
import com.zcommerce.shared.repository.ProductRepository;
import com.zcommerce.shared.repository.impl.DynamoDbCartRepository;
import com.zcommerce.shared.repository.impl.DynamoDbOrderRepository;
import com.zcommerce.shared.repository.impl.DynamoDbPaymentRepository;
import com.zcommerce.shared.repository.impl.DynamoDbProductRepository;
import com.zcommerce.shared.service.OrderIntegrationService;
import com.zcommerce.shared.util.ValidationUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Lambda handler for payment processing operations.
 * Handles payment processing and transaction management.
 * Uses mock payment gateway simulation for demonstration.
 */
public class PaymentHandler extends LambdaHandler {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderIntegrationService orderIntegrationService;
    private final Random random;

    // Configurable mock payment success rate (default 95%)
    private final double successRate;

    public PaymentHandler() {
        this(new DynamoDbPaymentRepository(), new DynamoDbOrderRepository(), 
             new DynamoDbCartRepository(), new DynamoDbProductRepository());
    }

    public PaymentHandler(PaymentRepository paymentRepository, OrderRepository orderRepository,
                         CartRepository cartRepository, ProductRepository productRepository) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.orderIntegrationService = new OrderIntegrationService(orderRepository, cartRepository, productRepository);
        this.random = new Random();

        String successRateEnv = System.getenv("PAYMENT_SUCCESS_RATE");
        this.successRate = successRateEnv != null ? Double.parseDouble(successRateEnv) : 0.95;
    }

    @Override
    protected String getServiceName() {
        return "payment-service";
    }

    @Override
    protected APIGatewayProxyResponseEvent processRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();

        logger.info("Processing payment service request",
                   Map.of("method", httpMethod, "path", path));

        // Route based on HTTP method and path
        if ("POST".equals(httpMethod) && path.matches(".*/payments/process$")) {
            return handleProcessPayment(request);
        } else if ("GET".equals(httpMethod) && path.matches(".*/payments/[^/]+$")) {
            return handleGetPayment(request);
        } else if ("POST".equals(httpMethod) && path.matches(".*/payments/[^/]+/refund$")) {
            return handleRefundPayment(request);
        }

        throw new ValidationException("Unsupported operation: " + httpMethod + " " + path);
    }

    /**
     * Handle process payment
     * POST /payments/process
     */
    private APIGatewayProxyResponseEvent handleProcessPayment(APIGatewayProxyRequestEvent request) {
        ProcessPaymentRequest paymentRequest = parseRequestBody(request.getBody(), ProcessPaymentRequest.class);

        // Validate input
        ValidationUtils.validateUUID(paymentRequest.orderId, "Order ID");
        ValidationUtils.validateRequired(paymentRequest.paymentMethod, "Payment method");

        // Validate payment method
        Payment.PaymentMethod paymentMethod;
        try {
            paymentMethod = Payment.PaymentMethod.valueOf(paymentRequest.paymentMethod.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid payment method: " + paymentRequest.paymentMethod);
        }

        // Validate payment details based on method
        validatePaymentDetails(paymentMethod, paymentRequest);

        // Get order
        Optional<Order> orderOpt = orderRepository.findById(paymentRequest.orderId);
        if (orderOpt.isEmpty()) {
            throw new ResourceNotFoundException("Order", paymentRequest.orderId);
        }

        Order order = orderOpt.get();

        // Check order status
        if (order.getStatus() != Order.OrderStatus.PENDING) {
            throw new ConflictException("Cannot process payment for order with status: " + order.getStatus());
        }

        // Create payment transaction
        String transactionId = UUID.randomUUID().toString();
        Payment payment = new Payment(transactionId, paymentRequest.orderId, order.getTotalAmount(), paymentMethod);
        payment.setStatus(Payment.PaymentStatus.PROCESSING);
        payment.setPaymentDetails(maskPaymentDetails(paymentRequest));

        // Simulate payment gateway processing
        boolean paymentSuccessful = simulatePaymentGateway(payment.getAmount());

        if (paymentSuccessful) {
            payment.setStatus(Payment.PaymentStatus.COMPLETED);
            payment.setProcessedAt(Instant.now());

            // Update order status to PROCESSING (payment received)
            order.setStatus(Order.OrderStatus.PROCESSING);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);

            // Complete the order (final step after successful payment)
            orderIntegrationService.completeOrder(order.getOrderId());

            logger.info("payment_successful", Map.of(
                "transactionId", transactionId,
                "orderId", paymentRequest.orderId,
                "amount", payment.getAmount(),
                "paymentMethod", paymentMethod
            ));
        } else {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            payment.setProcessedAt(Instant.now());

            // Fail the order and restore inventory
            orderIntegrationService.failOrder(order.getOrderId(), "Payment gateway declined");

            logger.info("payment_failed", Map.of(
                "transactionId", transactionId,
                "orderId", paymentRequest.orderId,
                "amount", payment.getAmount(),
                "reason", "Payment gateway declined"
            ));
        }

        paymentRepository.save(payment);

        Map<String, Object> response = toPaymentResponse(payment);
        response.put("orderId", order.getOrderId());
        response.put("orderStatus", order.getStatus().name());

        String message = paymentSuccessful ? "Payment processed successfully" : "Payment processing failed";
        return createSuccessResponse(response, message);
    }

    /**
     * Handle get payment details
     * GET /payments/{transactionId}
     */
    private APIGatewayProxyResponseEvent handleGetPayment(APIGatewayProxyRequestEvent request) {
        String transactionId = getPathParameter(request, "transactionId");
        ValidationUtils.validateUUID(transactionId, "Transaction ID");

        Optional<Payment> paymentOpt = paymentRepository.findById(transactionId);
        if (paymentOpt.isEmpty()) {
            throw new ResourceNotFoundException("Payment", transactionId);
        }

        Payment payment = paymentOpt.get();

        logger.info("payment_retrieved", Map.of(
            "transactionId", transactionId
        ));

        return createSuccessResponse(
            toPaymentResponse(payment),
            "Payment retrieved successfully"
        );
    }

    /**
     * Handle refund payment
     * POST /payments/{transactionId}/refund
     */
    private APIGatewayProxyResponseEvent handleRefundPayment(APIGatewayProxyRequestEvent request) {
        String transactionId = getPathParameter(request, "transactionId");
        ValidationUtils.validateUUID(transactionId, "Transaction ID");

        Optional<Payment> paymentOpt = paymentRepository.findById(transactionId);
        if (paymentOpt.isEmpty()) {
            throw new ResourceNotFoundException("Payment", transactionId);
        }

        Payment payment = paymentOpt.get();

        // Can only refund completed payments
        if (payment.getStatus() != Payment.PaymentStatus.COMPLETED) {
            throw new ConflictException("Cannot refund payment with status: " + payment.getStatus());
        }

        // Update payment status
        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        payment.setProcessedAt(Instant.now());
        paymentRepository.save(payment);

        // Cancel the order and restore inventory
        orderIntegrationService.cancelOrder(payment.getOrderId(), "Payment refunded");

        logger.info("payment_refunded", Map.of(
            "transactionId", transactionId,
            "orderId", payment.getOrderId(),
            "amount", payment.getAmount()
        ));

        return createSuccessResponse(
            toPaymentResponse(payment),
            "Payment refunded successfully"
        );
    }

    /**
     * Validate payment details based on payment method
     */
    private void validatePaymentDetails(Payment.PaymentMethod method, ProcessPaymentRequest request) {
        switch (method) {
            case CREDIT_CARD:
            case DEBIT_CARD:
                ValidationUtils.validateRequired(request.cardNumber, "Card number");
                ValidationUtils.validateRequired(request.cardHolder, "Card holder name");
                ValidationUtils.validateRequired(request.expiryDate, "Expiry date");
                ValidationUtils.validateRequired(request.cvv, "CVV");

                // Validate card number format (basic validation)
                if (request.cardNumber.replaceAll("\\s", "").length() < 13 ||
                    request.cardNumber.replaceAll("\\s", "").length() > 19) {
                    throw new ValidationException("Invalid card number format");
                }
                break;

            case PAYPAL:
                ValidationUtils.validateRequired(request.paypalEmail, "PayPal email");
                ValidationUtils.validateEmail(request.paypalEmail);
                break;

            case BANK_TRANSFER:
                ValidationUtils.validateRequired(request.bankAccount, "Bank account number");
                ValidationUtils.validateRequired(request.routingNumber, "Routing number");
                break;
        }
    }

    /**
     * Mask sensitive payment details for storage
     */
    private String maskPaymentDetails(ProcessPaymentRequest request) {
        if (request.cardNumber != null) {
            String masked = request.cardNumber.replaceAll("\\s", "");
            if (masked.length() >= 4) {
                return "****" + masked.substring(masked.length() - 4);
            }
        }
        if (request.paypalEmail != null) {
            int atIndex = request.paypalEmail.indexOf('@');
            if (atIndex > 2) {
                return request.paypalEmail.substring(0, 2) + "***" + request.paypalEmail.substring(atIndex);
            }
        }
        if (request.bankAccount != null && request.bankAccount.length() >= 4) {
            return "****" + request.bankAccount.substring(request.bankAccount.length() - 4);
        }
        return "****";
    }

    /**
     * Simulate payment gateway processing
     * Returns true for successful payment, false for failure
     */
    private boolean simulatePaymentGateway(BigDecimal amount) {
        // Simulate processing delay
        try {
            Thread.sleep(100); // 100ms simulated delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Mock validation: reject if amount is suspicious (very high)
        if (amount.compareTo(new BigDecimal("10000")) > 0) {
            logger.info("payment_gateway_high_amount_rejected", Map.of(
                "amount", amount,
                "reason", "Amount exceeds limit"
            ));
            return false;
        }

        // Random success/failure based on configured rate
        return random.nextDouble() < successRate;
    }

    /**
     * Convert Payment to response map
     */
    private Map<String, Object> toPaymentResponse(Payment payment) {
        Map<String, Object> response = new HashMap<>();
        response.put("transactionId", payment.getTransactionId());
        response.put("orderId", payment.getOrderId());
        response.put("amount", payment.getAmount());
        response.put("status", payment.getStatus().name());
        response.put("paymentMethod", payment.getPaymentMethod().name());
        response.put("paymentDetails", payment.getPaymentDetails());
        response.put("processedAt", payment.getProcessedAt() != null ? payment.getProcessedAt().toString() : null);
        return response;
    }

    // Request DTOs
    public static class ProcessPaymentRequest {
        public String orderId;
        public String paymentMethod;

        // Credit/Debit card fields
        public String cardNumber;
        public String cardHolder;
        public String expiryDate;
        public String cvv;

        // PayPal fields
        public String paypalEmail;

        // Bank transfer fields
        public String bankAccount;
        public String routingNumber;
    }
}
