package com.zcommerce.users;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.zcommerce.shared.api.LambdaHandler;
import com.zcommerce.shared.exception.AuthenticationException;
import com.zcommerce.shared.exception.ConflictException;
import com.zcommerce.shared.exception.ResourceNotFoundException;
import com.zcommerce.shared.exception.ValidationException;
import com.zcommerce.shared.model.User;
import com.zcommerce.shared.repository.UserRepository;
import com.zcommerce.shared.repository.impl.DynamoDbUserRepository;
import com.zcommerce.shared.util.ValidationUtils;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lambda handler for user management operations.
 * Handles user registration, authentication, profile management.
 */
public class UserHandler extends LambdaHandler {

    private final UserRepository userRepository;
    private final String jwtSecret;
    private final long jwtExpirationMs;

    public UserHandler() {
        this(new DynamoDbUserRepository());
    }

    public UserHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.jwtSecret = System.getenv("JWT_SECRET") != null ?
            System.getenv("JWT_SECRET") : "default-secret-for-development-only";
        this.jwtExpirationMs = Long.parseLong(
            System.getenv("JWT_EXPIRATION_MS") != null ?
            System.getenv("JWT_EXPIRATION_MS") : "3600000" // 1 hour default
        );
    }

    @Override
    protected String getServiceName() {
        return "user-service";
    }

    @Override
    protected APIGatewayProxyResponseEvent processRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();

        logger.info("Processing user service request",
                   Map.of("method", httpMethod, "path", path));

        // Route based on HTTP method and path
        if ("POST".equals(httpMethod) && path.endsWith("/register")) {
            return handleRegister(request);
        } else if ("POST".equals(httpMethod) && path.endsWith("/login")) {
            return handleLogin(request);
        } else if ("GET".equals(httpMethod) && path.matches(".*/users/[^/]+$")) {
            return handleGetProfile(request);
        } else if ("PUT".equals(httpMethod) && path.matches(".*/users/[^/]+$")) {
            return handleUpdateProfile(request);
        }

        throw new ValidationException("Unsupported operation: " + httpMethod + " " + path);
    }

    /**
     * Handle user registration
     * POST /users/register
     */
    private APIGatewayProxyResponseEvent handleRegister(APIGatewayProxyRequestEvent request) {
        RegisterRequest registerRequest = parseRequestBody(request.getBody(), RegisterRequest.class);

        // Validate input
        ValidationUtils.validateEmail(registerRequest.email);
        ValidationUtils.validatePassword(registerRequest.password);
        ValidationUtils.validateRequired(registerRequest.firstName, "First name");
        ValidationUtils.validateRequired(registerRequest.lastName, "Last name");

        // Check if email already exists
        if (userRepository.existsByEmail(registerRequest.email)) {
            throw new ConflictException("Email already registered: " + registerRequest.email);
        }

        // Hash password
        String passwordHash = BCrypt.withDefaults().hashToString(12, registerRequest.password.toCharArray());

        // Create user
        String userId = UUID.randomUUID().toString();
        User user = new User(userId, registerRequest.email, passwordHash,
                            registerRequest.firstName, registerRequest.lastName);

        userRepository.save(user);

        logger.info("user_registered", Map.of(
            "userId", userId,
            "email", registerRequest.email
        ));

        // Generate token
        String token = generateToken(user);

        return createSuccessResponse(
            Map.of(
                "userId", userId,
                "email", user.getEmail(),
                "firstName", user.getFirstName(),
                "lastName", user.getLastName(),
                "token", token
            ),
            "User registered successfully"
        );
    }

    /**
     * Handle user login
     * POST /users/login
     */
    private APIGatewayProxyResponseEvent handleLogin(APIGatewayProxyRequestEvent request) {
        LoginRequest loginRequest = parseRequestBody(request.getBody(), LoginRequest.class);

        // Validate input
        ValidationUtils.validateEmail(loginRequest.email);
        ValidationUtils.validateRequired(loginRequest.password, "Password");

        // Find user by email
        Optional<User> userOpt = userRepository.findByEmail(loginRequest.email);
        if (userOpt.isEmpty()) {
            throw new AuthenticationException("Invalid email or password");
        }

        User user = userOpt.get();

        // Verify password
        BCrypt.Result result = BCrypt.verifyer().verify(loginRequest.password.toCharArray(), user.getPasswordHash());
        if (!result.verified) {
            throw new AuthenticationException("Invalid email or password");
        }

        logger.info("user_logged_in", Map.of(
            "userId", user.getUserId(),
            "email", user.getEmail()
        ));

        // Generate token
        String token = generateToken(user);

        return createSuccessResponse(
            Map.of(
                "userId", user.getUserId(),
                "email", user.getEmail(),
                "firstName", user.getFirstName(),
                "lastName", user.getLastName(),
                "token", token
            ),
            "Login successful"
        );
    }

    /**
     * Handle get user profile
     * GET /users/{userId}
     */
    private APIGatewayProxyResponseEvent handleGetProfile(APIGatewayProxyRequestEvent request) {
        String userId = getPathParameter(request, "userId");

        // Validate userId format
        ValidationUtils.validateUUID(userId, "User ID");

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new ResourceNotFoundException("User", userId);
        }

        User user = userOpt.get();

        logger.info("user_profile_retrieved", Map.of(
            "userId", userId
        ));

        return createSuccessResponse(
            Map.of(
                "userId", user.getUserId(),
                "email", user.getEmail(),
                "firstName", user.getFirstName(),
                "lastName", user.getLastName(),
                "createdAt", user.getCreatedAt().toString(),
                "updatedAt", user.getUpdatedAt().toString()
            ),
            "User profile retrieved successfully"
        );
    }

    /**
     * Handle update user profile
     * PUT /users/{userId}
     */
    private APIGatewayProxyResponseEvent handleUpdateProfile(APIGatewayProxyRequestEvent request) {
        String userId = getPathParameter(request, "userId");

        // Validate userId format
        ValidationUtils.validateUUID(userId, "User ID");

        UpdateProfileRequest updateRequest = parseRequestBody(request.getBody(), UpdateProfileRequest.class);

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new ResourceNotFoundException("User", userId);
        }

        User user = userOpt.get();

        // Update fields if provided
        if (updateRequest.firstName != null && !updateRequest.firstName.trim().isEmpty()) {
            user.setFirstName(updateRequest.firstName);
        }
        if (updateRequest.lastName != null && !updateRequest.lastName.trim().isEmpty()) {
            user.setLastName(updateRequest.lastName);
        }
        if (updateRequest.email != null && !updateRequest.email.trim().isEmpty()) {
            ValidationUtils.validateEmail(updateRequest.email);

            // Check if new email already exists for different user
            Optional<User> existingUser = userRepository.findByEmail(updateRequest.email);
            if (existingUser.isPresent() && !existingUser.get().getUserId().equals(userId)) {
                throw new ConflictException("Email already in use: " + updateRequest.email);
            }

            user.setEmail(updateRequest.email);
        }
        if (updateRequest.password != null && !updateRequest.password.trim().isEmpty()) {
            ValidationUtils.validatePassword(updateRequest.password);
            String passwordHash = BCrypt.withDefaults().hashToString(12, updateRequest.password.toCharArray());
            user.setPasswordHash(passwordHash);
        }

        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        logger.info("user_profile_updated", Map.of(
            "userId", userId
        ));

        return createSuccessResponse(
            Map.of(
                "userId", user.getUserId(),
                "email", user.getEmail(),
                "firstName", user.getFirstName(),
                "lastName", user.getLastName(),
                "updatedAt", user.getUpdatedAt().toString()
            ),
            "User profile updated successfully"
        );
    }

    /**
     * Generate JWT token for user
     */
    private String generateToken(User user) {
        Algorithm algorithm = Algorithm.HMAC256(jwtSecret);
        return JWT.create()
            .withSubject(user.getUserId())
            .withClaim("email", user.getEmail())
            .withClaim("firstName", user.getFirstName())
            .withClaim("lastName", user.getLastName())
            .withIssuedAt(new Date())
            .withExpiresAt(new Date(System.currentTimeMillis() + jwtExpirationMs))
            .sign(algorithm);
    }

    /**
     * Validate JWT token and return user ID
     */
    public String validateToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(jwtSecret);
            DecodedJWT jwt = JWT.require(algorithm).build().verify(token);
            return jwt.getSubject();
        } catch (JWTVerificationException e) {
            throw new AuthenticationException("Invalid or expired token");
        }
    }

    // Request DTOs
    public static class RegisterRequest {
        public String email;
        public String password;
        public String firstName;
        public String lastName;
    }

    public static class LoginRequest {
        public String email;
        public String password;
    }

    public static class UpdateProfileRequest {
        public String email;
        public String password;
        public String firstName;
        public String lastName;
    }
}
