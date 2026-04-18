package com.example.k8scomp.service;

import com.example.k8scomp.model.User;
import com.example.k8scomp.repository.UserRepository;
import com.example.k8scomp.security.JwtUtils;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
public class AuthService {

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
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setVerified(false);
        user.setCreatedAt(java.time.LocalDateTime.now());
        String otp = String.format("%06d", new Random().nextInt(999999));
        user.setOtp(otp);
        userRepository.save(user);
        sendEmail(email, "K8s Comparator OTP Verification", "Your OTP is: " + otp);
    }

    public boolean verifyOtp(String email, String otp) {
        return userRepository.findByEmail(email)
                .map(user -> {
                    if (otp.equals(user.getOtp())) {
                        user.setVerified(true);
                        user.setOtp(null);
                        userRepository.save(user);
                        return true;
                    }
                    return false;
                }).orElse(false);
    }

    /**
     * Authenticates a user and returns { token, userId, email } or null.
     */
    public Map<String, String> login(String email, String password) {
        return userRepository.findByEmail(email)
                .filter(User::isVerified)
                .filter(user -> passwordEncoder.matches(password, user.getPassword()))
                .map(user -> Map.of(
                    "token",  jwtUtils.generateToken(email),
                    "userId", user.getId(),
                    "email",  user.getEmail()
                ))
                .orElse(null);
    }

    /**
     * Initiates a password reset: generates a reset token and emails it.
     */
    public boolean forgotPassword(String email) {
        return userRepository.findByEmail(email).map(user -> {
            String resetToken = UUID.randomUUID().toString();
            user.setOtp(resetToken); // reuse OTP field for reset token
            userRepository.save(user);
            sendEmail(email,
                "K8s Comparator — Password Reset",
                "Your password reset token is: " + resetToken +
                "\n\nUse this in the Reset Password screen within 15 minutes.");
            return true;
        }).orElse(false);
    }

    /**
     * Resets the password if the token matches.
     */
    public boolean resetPassword(String email, String token, String newPassword) {
        return userRepository.findByEmail(email).map(user -> {
            if (token.equals(user.getOtp())) {
                user.setPassword(passwordEncoder.encode(newPassword));
                user.setOtp(null);
                userRepository.save(user);
                return true;
            }
            return false;
        }).orElse(false);
    }

    private void sendEmail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
