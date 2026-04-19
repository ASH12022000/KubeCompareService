package com.example.k8scomp.service;

import com.example.k8scomp.model.User;
import com.example.k8scomp.repository.UserRepository;
import com.example.k8scomp.security.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.k8scomp.repository.HistoryRepository;
import com.example.k8scomp.repository.BaselineRepository;

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
    private final HistoryRepository historyRepository;
    private final BaselineRepository baselineRepository;

    @Value("${app.otp.expiry.minutes:5}")
    private int otpExpiryMinutes;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JavaMailSender mailSender, JwtUtils jwtUtils,
                       HistoryRepository historyRepository, BaselineRepository baselineRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.jwtUtils = jwtUtils;
        this.historyRepository = historyRepository;
        this.baselineRepository = baselineRepository;
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
        user.setOtpExpiry(java.time.LocalDateTime.now().plusMinutes(otpExpiryMinutes));
        userRepository.save(user);
        log.debug("User saved to DB: email={}, id={}", email, user.getId());
        sendEmail(email, "K8s Comparator OTP Verification", "Your OTP is: " + otp + "\n\nThis OTP will expire in " + otpExpiryMinutes + " minutes.");
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
            user.setOtpExpiry(java.time.LocalDateTime.now().plusMinutes(otpExpiryMinutes));
            userRepository.save(user);
            sendEmail(email, "K8s Comparator OTP Verification", "Your new OTP is: " + otp + "\n\nThis OTP will expire in " + otpExpiryMinutes + " minutes.");
            log.info("New OTP dispatched to {}", email);
            return true;
        }).orElse(false);
    }

    public boolean verifyOtp(String email, String otp) {
        log.info("Verifying OTP for email={}", email);
        return userRepository.findByEmail(email)
                .map(user -> {
                    if (otp.equals(user.getOtp())) {
                        if (user.getOtpExpiry() != null && user.getOtpExpiry().isBefore(java.time.LocalDateTime.now())) {
                            log.warn("OTP expired for email={}", email);
                            return false;
                        }
                        user.setVerified(true);
                        user.setOtp(null);
                        user.setOtpExpiry(null);
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
            user.setOtpExpiry(java.time.LocalDateTime.now().plusMinutes(otpExpiryMinutes));
            userRepository.save(user);
            sendEmail(email,
                "K8s Comparator — Password Reset",
                "Your password reset token is: " + resetToken +
                "\n\nUse this in the Reset Password screen within " + otpExpiryMinutes + " minutes.");
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
                if (user.getOtpExpiry() != null && user.getOtpExpiry().isBefore(java.time.LocalDateTime.now())) {
                    log.warn("Password reset FAILED — token expired: email={}", email);
                    return false;
                }
                user.setPassword(passwordEncoder.encode(newPassword));
                user.setOtp(null);
                user.setOtpExpiry(null);
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

    private Optional<User> findByIdOrEmail(String identifier) {
        Optional<User> userOpt = userRepository.findById(identifier);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(identifier);
        }
        return userOpt;
    }

    public Map<String, Object> getProfile(String userId) {
        return findByIdOrEmail(userId).map(user -> {
            return Map.<String, Object>of(
                "email", user.getEmail(),
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : "",
                "organization", user.getOrganization() != null ? user.getOrganization() : "",
                "createdAt", user.getCreatedAt()
            );
        }).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public void updateProfile(String userId, String displayName, String organization) {
        log.info("Updating profile for userId={}: displayName={}, organization={}", userId, displayName, organization);
        findByIdOrEmail(userId).ifPresentOrElse(user -> {
            user.setDisplayName(displayName);
            user.setOrganization(organization);
            userRepository.save(user);
            log.info("Profile updated in DB for user={}", user.getEmail());
        }, () -> {
            log.error("Failed to update profile: User not found for id/email={}", userId);
        });
    }

    public boolean sendChangePasswordOtp(String userId) {
        return findByIdOrEmail(userId).map(user -> {
            String otp = String.format("%06d", new Random().nextInt(999999));
            user.setOtp(otp);
            user.setOtpExpiry(java.time.LocalDateTime.now().plusMinutes(otpExpiryMinutes));
            userRepository.save(user);
            sendEmail(user.getEmail(), "K8s Comparator Password Change OTP", "Your OTP to change password is: " + otp + "\n\nThis OTP will expire in " + otpExpiryMinutes + " minutes.");
            log.info("Change password OTP dispatched to user {}", userId);
            return true;
        }).orElse(false);
    }

    public boolean changePassword(String userId, String oldPassword, String newPassword, String otp) {
        return findByIdOrEmail(userId).map(user -> {
            if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                log.warn("Change password failed — old password mismatch for user {}", userId);
                return false;
            }
            if (otp != null && otp.equals(user.getOtp())) {
                if (user.getOtpExpiry() != null && user.getOtpExpiry().isBefore(java.time.LocalDateTime.now())) {
                    log.warn("Change password failed — OTP expired for user {}", userId);
                    return false;
                }
                user.setPassword(passwordEncoder.encode(newPassword));
                user.setOtp(null);
                user.setOtpExpiry(null);
                userRepository.save(user);
                log.info("Password changed successfully for user {}", userId);
                return true;
            }
            log.warn("Change password failed — OTP mismatch for user {}", userId);
            return false;
        }).orElse(false);
    }

    public void deleteAccount(String userId) {
        findByIdOrEmail(userId).ifPresent(user -> {
            String actualUserId = user.getId();
            log.info("Deleting account and all associated data for user {}", actualUserId);
            
            // Cascade delete history and baselines
            historyRepository.findTop10ByUserIdOrderByTimestampDesc(actualUserId).forEach(h -> historyRepository.deleteById(h.getId()));
            baselineRepository.findByUserId(actualUserId).forEach(b -> baselineRepository.deleteById(b.getId()));
            
            userRepository.deleteById(actualUserId);
            log.info("Account deleted successfully for user {}", actualUserId);
        });
    }

    public void reportBug(String userId, String subject, String description) {
        findByIdOrEmail(userId).ifPresent(user -> {
            String fullSubject = "Bug Report from " + user.getEmail() + ": " + subject;
            sendEmail("mahia020005@gmail.com", fullSubject, description + "\n\nUser ID: " + userId + "\nEmail: " + user.getEmail());
            log.info("Bug report sent by user {}", userId);
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
