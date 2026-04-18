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
            return ResponseEntity.ok(Map.of("message", "Verified"));
        return ResponseEntity.status(401).body(Map.of("error", "Invalid OTP"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String token = authService.login(request.get("email"), request.get("password"));
        if (token != null)
            return ResponseEntity.ok(Map.of("token", token));
        return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials or not verified"));
    }
}
