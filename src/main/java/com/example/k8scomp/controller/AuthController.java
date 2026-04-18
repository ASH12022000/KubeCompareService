package com.example.k8scomp.controller;

import com.example.k8scomp.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> request) {
        authService.signup(request.get("email"), request.get("password"));
        return ResponseEntity.ok(Map.of("message", "OTP sent to email"));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, String> request) {
        boolean ok = authService.verifyOtp(request.get("email"), request.get("otp"));
        if (ok)
            return ResponseEntity.ok(Map.of("message", "Verified. You can now log in."));
        return ResponseEntity.status(401).body(Map.of("error", "Invalid OTP"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        Map<String, String> result = authService.login(request.get("email"), request.get("password"));
        if (result != null)
            return ResponseEntity.ok(result); // { token, userId, email }
        return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials or account not verified"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        boolean sent = authService.forgotPassword(request.get("email"));
        // Always return 200 to avoid email enumeration attacks
        return ResponseEntity.ok(Map.of("message", "If this email is registered, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        boolean ok = authService.resetPassword(
            request.get("email"),
            request.get("token"),
            request.get("newPassword")
        );
        if (ok)
            return ResponseEntity.ok(Map.of("message", "Password reset successfully."));
        return ResponseEntity.status(400).body(Map.of("error", "Invalid or expired token."));
    }
}
