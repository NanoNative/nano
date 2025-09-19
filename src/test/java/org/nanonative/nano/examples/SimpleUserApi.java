package org.nanonative.nano.examples;

import org.junit.jupiter.api.Disabled;
import org.nanonative.nano.core.Nano;
import org.nanonative.nano.helper.event.model.Event;
import org.nanonative.nano.services.http.HttpServer;
import org.nanonative.nano.services.http.model.HttpObject;

import java.util.List;
import java.util.Map;

import static org.nanonative.nano.services.http.HttpServer.EVENT_HTTP_REQUEST;

/**
 * Simple user API example demonstrating Nano best practices.
 * <p>
 * This example shows:
 * - Proper separation of concerns (static methods for business logic)
 * - Event-driven communication
 * - CORS handling
 * - Error handling
 * - Request/response patterns
 * <p>
 * Based on real-world patterns and developer feedback.
 */
@Disabled
public class SimpleUserApi {

    public static void main(String[] args) {
        // Start Nano with HTTP server
        final Nano nano = new Nano(args, new HttpServer());

        // Register middleware (order matters!)
        nano.subscribeEvent(EVENT_HTTP_REQUEST, SimpleUserApi::handleCors);
        nano.subscribeEvent(EVENT_HTTP_REQUEST, SimpleUserApi::handleAuth);

        // Register API endpoints
        nano.subscribeEvent(EVENT_HTTP_REQUEST, SimpleUserApi::handleRegister);
        nano.subscribeEvent(EVENT_HTTP_REQUEST, SimpleUserApi::handleLogin);
        nano.subscribeEvent(EVENT_HTTP_REQUEST, SimpleUserApi::handleGetUser);

        // Global error handling
        nano.subscribeError(EVENT_HTTP_REQUEST, SimpleUserApi::handleError);
    }

    // ==================== MIDDLEWARE ====================
    // Could get into a separate class/file for larger apps

    /**
     * CORS handling - must be first middleware
     */
    private static void handleCors(final Event<HttpObject, HttpObject> event) {
        event.payloadOpt()
            .filter(HttpObject::isMethodOptions)
            .ifPresent(req -> req.createCorsResponse().respond(event));
    }

    /**
     * Authentication middleware
     */
    private static void handleAuth(final Event<HttpObject, HttpObject> event) {
        event.payloadOpt()
            .filter(req -> req.pathMatch("/api/**"))
            .filter(req -> !req.pathMatch("/api/auth/**")) // Skip auth for login/register
            .ifPresent(req -> {
                final String token = req.authToken();
                if (!isValidToken(token)) {
                    req.createCorsResponse().failure(401, "Authentication required", null).respond(event);
                } else {
                    event.put("scopes", List.of("extracted:from:token:write", "read", "something")); // Example: add scopes to event context
                }
            });
    }

    /**
     * Global error handler
     */
    private static void handleError(final Event<HttpObject, HttpObject> event) {
        // Handle HTTP request errors
        event.payload().failure(500, event.error()).respond(event);
    }

    // ==================== API ENDPOINTS ====================

    /**
     * User registration endpoint
     */
    private static void handleRegister(final Event<HttpObject, HttpObject> event) {
        event.payloadOpt()
            .filter(HttpObject::isMethodPost)
            .filter(req -> req.pathMatch("/api/auth/register"))
            .filter(req -> event.asList("scopes").contains("write")) // Example scope check
            .ifPresent(req -> {
                final var body = req.bodyAsJson().asMap();

                // Validate input
                if (!isValidRegistration(body)) {
                    sendError(req, event, 400, "Invalid registration data");
                    return;
                }

                // Create user (in real app, this would go to database service)
                final User user = createUser(body.asString("email"), body.asString("name"));

                // Send success response
                req.createCorsResponse().statusCode(201).body(Map.of(
                    "id", user.id(),
                    "email", user.email(),
                    "name", user.name(),
                    "message", "User created successfully"
                )).respond(event);
            });
    }

    /**
     * User login endpoint
     */
    private static void handleLogin(Event<HttpObject, HttpObject> event) {
        event.payloadOpt()
            .filter(HttpObject::isMethodPost)
            .filter(req -> req.pathMatch("/api/auth/login"))
            .ifPresent(req -> {
                final var body = req.bodyAsJson().asMap();

                final String email = body.asString("email");
                final String password = body.asString("password");

                // Simulate authentication
                if (authenticateUser(email, password)) {
                    req.createCorsResponse()
                        .statusCode(200)
                        .body(Map.of(
                            "token", generateToken(email),
                            "user", Map.of(
                                "email", email,
                                "name", getUserName(email)
                            ),
                            "message", "Login successful"
                        ))
                        .respond(event);
                } else {
                    sendError(req, event, 401, "Invalid credentials");
                }
            });
    }

    /**
     * Get user profile endpoint
     */
    private static void handleGetUser(final Event<HttpObject, HttpObject> event) {
        event.payloadOpt()
            .filter(HttpObject::isMethodGet)
            .filter(req -> req.pathMatch("/api/users/me"))
            .ifPresent(req -> {
                final String token = req.authToken();
                final String email = getUserEmailFromToken(token);

                if (email != null) {
                    req.createResponse()
                        .statusCode(200)
                        .body(Map.of(
                            "email", email,
                            "name", getUserName(email),
                            "lastLogin", System.currentTimeMillis()
                        ))
                        .respond(event);
                } else {
                    sendError(req, event, 401, "Invalid token");
                }
            });
    }

    // ==================== HELPER METHODS ====================

    private static boolean isValidRegistration(berlin.yuna.typemap.model.TypeMapI<?> body) {
        final String email = body.asString("email");
        final String name = body.asString("name");
        final String password = body.asString("password");

        return email != null && email.contains("@") &&
            name != null && !name.trim().isEmpty() &&
            password != null && password.length() >= 8;
    }

    private static void sendError(HttpObject req, Event<?, ?> event, int status, String message) {
        req.createResponse()
            .statusCode(status)
            .body(Map.of("error", message))
            .respond(event);
    }

    // ==================== MOCK DATA & METHODS ====================

    private static boolean isValidToken(String token) {
        return token != null && token.startsWith("token_");
    }

    private static User createUser(String email, String name) {
        return new User(System.currentTimeMillis(), email, name);
    }

    private static boolean authenticateUser(final String email, String password) {
        // Simple mock authentication
        return email != null && password != null && password.length() >= 8;
    }

    private static String generateToken(final String email) {
        return "token_" + email + "_" + System.currentTimeMillis();
    }

    private static String getUserEmailFromToken(final String token) {
        if (token == null || !token.startsWith("token_")) {
            return null;
        }
        // Extract email from token (simplified)
        final String[] parts = token.split("_");
        return parts.length > 1 ? parts[1] : null;
    }

    private static String getUserName(final String email) {
        // Mock user data
        return email.split("@")[0];
    }

    // ==================== DATA MODEL ====================

    /**
     * Simple User model
     */
    public record User(Long id, String email, String name) {

    }
}
