package com.example.k8scomp.service;

import com.example.k8scomp.model.User;
import com.example.k8scomp.repository.UserRepository;
import com.example.k8scomp.security.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final JwtUtils jwtUtils;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JavaMailSender mailSender, JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.jwtUtils = jwtUtils;
    }

    public void signup(String email, String password) {
        log.info("Signup request: email={}", email);

        // Check for existing registered user
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent() && existing.get().isVerified()) {
            log.warn("Signup rejected — email already registered and verified: {}", email);
            throw new IllegalArgumentException("Email already registered");
        }
        // If unverified duplicate exists, delete and re-register
        existing.ifPresent(u -> {
            log.info("Removing stale unverified registration for email={}", email);
            userRepository.delete(u);
        });

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setVerified(false);
        user.setCreatedAt(java.time.LocalDateTime.now());
        String otp = String.format("%06d", new Random().nextInt(999999));
        user.setOtp(otp);
        userRepository.save(user);
        log.debug("User saved to DB: email={}, id={}", email, user.getId());
        sendEmail(email, "K8s Comparator OTP Verification", "Your OTP is: " + otp);
        log.info("OTP email dispatched to {}", email);
    }

    public boolean resendOtp(String email) {
        log.info("Resend OTP requested for email={}", email);
        return userRepository.findByEmail(email).map(user -> {
            if (user.isVerified()) {
                log.warn("Resend OTP skipped — account already verified: {}", email);
                return false;
            }
            String otp = String.format("%06d", new Random().nextInt(999999));
            user.setOtp(otp);
            userRepository.save(user);
            sendEmail(email, "K8s Comparator OTP Verification", "Your new OTP is: " + otp);
            log.info("New OTP dispatched to {}", email);
            return true;
        }).orElse(false);
    }

    public boolean verifyOtp(String email, String otp) {
        log.info("Verifying OTP for email={}", email);
        return userRepository.findByEmail(email)
                .map(user -> {
                    if (otp.equals(user.getOtp())) {
                        user.setVerified(true);
                        user.setOtp(null);
                        userRepository.save(user);
                        log.info("OTP verified — user activated: email={}", email);
                        return true;
                    }
                    log.warn("OTP mismatch for email={}", email);
                    return false;
                }).orElseGet(() -> {
                    log.warn("OTP verify — user not found: email={}", email);
                    return false;
                });
    }

    public Map<String, String> login(String email, String password) {
        log.info("Login attempt: email={}", email);
        return userRepository.findByEmail(email)
                .filter(user -> {
                    if (!user.isVerified()) { log.warn("Login blocked — unverified account: email={}", email); return false; }
                    return true;
                })
                .filter(user -> {
                    boolean ok = passwordEncoder.matches(password, user.getPassword());
                    if (!ok) log.warn("Login failed — password mismatch: email={}", email);
                    return ok;
                })
                .map(user -> {
                    String token = jwtUtils.generateToken(email);
                    log.info("JWT issued for email={}, userId={}", email, user.getId());
                    return Map.of("token", token, "userId", user.getId(), "email", user.getEmail());
                })
                .orElse(null);
    }

    public boolean forgotPassword(String email) {
        log.info("Forgot-password flow started: email={}", email);
        return userRepository.findByEmail(email).map(user -> {
            String resetToken = UUID.randomUUID().toString();
            user.setOtp(resetToken);
            userRepository.save(user);
            sendEmail(email,
                "K8s Comparator — Password Reset",
                "Your password reset token is: " + resetToken +
                "\n\nUse this in the Reset Password screen within 15 minutes.");
            log.info("Reset token generated and emailed for email={}", email);
            return true;
        }).orElseGet(() -> {
            log.warn("Forgot-password — email not found: {}", email);
            return false;
        });
    }

    public boolean resetPassword(String email, String token, String newPassword) {
        log.info("Password reset attempt: email={}", email);
        return userRepository.findByEmail(email).map(user -> {
            if (token.equals(user.getOtp())) {
                user.setPassword(passwordEncoder.encode(newPassword));
                user.setOtp(null);
                userRepository.save(user);
                log.info("Password reset SUCCESS: email={}", email);
                return true;
            }
            log.warn("Password reset FAILED — token mismatch: email={}", email);
            return false;
        }).orElseGet(() -> {
            log.warn("Password reset — user not found: email={}", email);
            return false;
        });
    }

    private void sendEmail(String to, String subject, String text) {
        log.debug("Sending email to={}, subject={}", to, subject);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.debug("Email sent successfully to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
        }
    }
}
