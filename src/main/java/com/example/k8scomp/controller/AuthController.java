package com.example.k8scomp.controller;

import com.example.k8scomp.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        log.info("Signup request received for email={}", email);
        try {
            authService.signup(email, request.get("password"));
            log.info("Signup OTP sent successfully to email={}", email);
            return ResponseEntity.ok(Map.of("message", "OTP sent to email"));
        } catch (IllegalArgumentException e) {
            log.warn("Signup rejected: {}", e.getMessage());
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        log.info("Resend OTP request for email={}", email);
        boolean sent = authService.resendOtp(email);
        if (sent) return ResponseEntity.ok(Map.of("message", "New OTP sent to email"));
        return ResponseEntity.status(400).body(Map.of("error", "Could not resend OTP"));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        log.info("OTP verification attempt for email={}", email);
        boolean ok = authService.verifyOtp(email, request.get("otp"));
        if (ok) {
            log.info("OTP verified successfully for email={}", email);
            return ResponseEntity.ok(Map.of("message", "Verified. You can now log in."));
        }
        log.warn("OTP verification FAILED for email={}", email);
        return ResponseEntity.status(401).body(Map.of("error", "Invalid OTP"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        log.info("Login attempt for email={}", email);
        Map<String, Object> result = authService.login(email, request.get("password"));
        if (result != null) {
            log.info("Login SUCCESS for email={}, userId={}", email, result.get("userId"));
            return ResponseEntity.ok(result);
        }
        log.warn("Login FAILED for email={} — invalid credentials or unverified account", email);
        return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials or account not verified"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Refresh token is missing"));
        }
        try {
            Map<String, Object> newTokens = authService.refreshToken(refreshToken);
            return ResponseEntity.ok(newTokens);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        log.info("Forgot-password request for email={}", email);
        authService.forgotPassword(email);
        return ResponseEntity.ok(Map.of("message", "If this email is registered, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        log.info("Reset-password attempt for email={}", email);
        boolean ok = authService.resetPassword(email, request.get("token"), request.get("newPassword"));
        if (ok) {
            log.info("Password reset SUCCESS for email={}", email);
            return ResponseEntity.ok(Map.of("message", "Password reset successfully."));
        }
        log.warn("Password reset FAILED for email={} — invalid or expired token", email);
        return ResponseEntity.status(400).body(Map.of("error", "Invalid or expired token."));
    }

    @GetMapping("/profile/{userId}")
    public ResponseEntity<?> getProfile(@PathVariable String userId) {
        try {
            return ResponseEntity.ok(authService.getProfile(userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/profile/{userId}")
    public ResponseEntity<?> updateProfile(@PathVariable String userId, @RequestBody Map<String, String> request) {
        log.info("Update profile request for userId={}", userId);
        authService.updateProfile(userId, request.get("displayName"), request.get("organization"));
        return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
    }

    @PostMapping("/send-change-password-otp")
    public ResponseEntity<?> sendChangePasswordOtp(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        boolean sent = authService.sendChangePasswordOtp(userId);
        if (sent) return ResponseEntity.ok(Map.of("message", "OTP sent to your email"));
        return ResponseEntity.status(400).body(Map.of("error", "Could not send OTP"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        boolean changed = authService.changePassword(
            userId, 
            request.get("oldPassword"), 
            request.get("newPassword"), 
            request.get("otp")
        );
        if (changed) return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
        return ResponseEntity.status(400).body(Map.of("error", "Invalid OTP or old password"));
    }

    @DeleteMapping("/account/{userId}")
    public ResponseEntity<?> deleteAccount(@PathVariable String userId, @RequestBody Map<String, String> request) {
        String confirmEmail = request.get("confirmEmail");
        try {
            Map<String, Object> profile = authService.getProfile(userId);
            if (!profile.get("email").equals(confirmEmail)) {
                return ResponseEntity.status(400).body(Map.of("error", "Email does not match"));
            }
            authService.deleteAccount(userId);
            return ResponseEntity.ok(Map.of("message", "Account deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/report-bug")
    public ResponseEntity<?> reportBug(@RequestBody Map<String, String> request) {
        authService.reportBug(request.get("userId"), request.get("subject"), request.get("description"));
        return ResponseEntity.ok(Map.of("message", "Bug report sent successfully"));
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validateToken() {
        return ResponseEntity.ok(Map.of("valid", true));
    }
}
