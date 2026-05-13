package com.codesync.auth.controller;

import com.codesync.auth.dto.AuthResponse;
import com.codesync.auth.model.User;
import com.codesync.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration, login, and token management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                authService.register(
                        request.getUsername(),
                        request.getEmail(),
                        request.getPassword(),
                        request.getFullName()
                )
        );
    }

    @PostMapping("/login")
    @Operation(summary = "Login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(
                authService.login(request.getEmail(), request.getPassword())
        );
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<Map<String, Object>> me(@RequestHeader("X-User-Id") Long userId) {
        User user = authService.getUserById(userId);

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "fullName", user.getFullName() == null ? "" : user.getFullName(),
                "role", user.getRole().name()
        ));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update user profile")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {

        User user = authService.updateProfile(
                userId,
                request.getFullName(),
                request.getUsername()
        );

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "fullName", user.getFullName() == null ? "" : user.getFullName()
        ));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password")
    public ResponseEntity<Void> changePassword(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ChangePasswordRequest request) {

        authService.changePassword(
                userId,
                request.getOldPassword(),
                request.getNewPassword()
        );

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/deactivate")
    @Operation(summary = "Deactivate account")
    public ResponseEntity<Void> deactivateAccount(@RequestHeader("X-User-Id") Long userId) {
        authService.deactivateAccount(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/search")
    @Operation(summary = "Search users")
    public ResponseEntity<Page<Map<String, Object>>> searchUsers(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<User> users = authService.searchUsers(query, PageRequest.of(page, size));

        return ResponseEntity.ok(users.map(u -> Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "email", u.getEmail(),
                "fullName", u.getFullName() == null ? "" : u.getFullName()
        )));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<AuthResponse> refresh(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");

        if (refreshToken == null) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(authService.refreshToken(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map<String, String> request) {

        String refreshToken = request != null ? request.get("refreshToken") : null;

        authService.logout(authHeader, refreshToken);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/validate")
    @Operation(summary = "Validate token")
    public ResponseEntity<Boolean> validateToken(@RequestParam String token) {
        return ResponseEntity.ok(!authService.isTokenBlacklisted(token));
    }

    // ================= ADMIN ENDPOINTS =================

    @GetMapping("/admin/users")
    @Operation(summary = "Admin - Get all users")
    public ResponseEntity<Page<Map<String, Object>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<User> users = authService.getAllUsers(PageRequest.of(page, size));

        return ResponseEntity.ok(users.map(u -> Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "email", u.getEmail(),
                "fullName", u.getFullName() == null ? "" : u.getFullName(),
                "role", u.getRole().name(),
                "enabled", u.isEnabled()
        )));
    }

    @GetMapping("/admin/stats")
    @Operation(summary = "Admin - User stats")
    public ResponseEntity<Map<String, Long>> getAdminStats() {
        return ResponseEntity.ok(authService.getAdminStats());
    }

    @PatchMapping("/admin/users/{id}/activate")
    @Operation(summary = "Admin - Activate user")
    public ResponseEntity<Void> activateUser(@PathVariable Long id) {
        authService.activateUser(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/admin/users/{id}/deactivate")
    @Operation(summary = "Admin - Deactivate user")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id) {
        authService.deactivateUserByAdmin(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/admin/users/{id}")
    @Operation(summary = "Admin - Delete user")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        authService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    // ================= DTOs =================

    @Data
    public static class RegisterRequest {
        @NotBlank
        @Size(min = 3, max = 50)
        private String username;

        @NotBlank
        @Email
        private String email;

        @NotBlank
        @Size(min = 8)
        private String password;

        private String fullName;
    }

    @Data
    public static class LoginRequest {
        @NotBlank
        @Email
        private String email;

        @NotBlank
        private String password;
    }

    @Data
    public static class UpdateProfileRequest {
        private String fullName;
        private String username;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank
        private String oldPassword;

        @NotBlank
        @Size(min = 8)
        private String newPassword;
    }
}

