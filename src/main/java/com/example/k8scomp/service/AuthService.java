package com.example.k8scomp.service;

import com.example.k8scomp.model.User;
import com.example.k8scomp.repository.UserRepository;
import com.example.k8scomp.security.JwtUtils;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Random;

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
        sendOtpEmail(email, otp);
    }

    private void sendOtpEmail(String email, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("K8s Comparator OTP Verification");
        message.setText("Your OTP is: " + otp);
        mailSender.send(message);
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

    public String login(String email, String password) {
        return userRepository.findByEmail(email)
                .filter(User::isVerified)
                .filter(user -> passwordEncoder.matches(password, user.getPassword()))
                .map(user -> jwtUtils.generateToken(email))
                .orElse(null);
    }
}
