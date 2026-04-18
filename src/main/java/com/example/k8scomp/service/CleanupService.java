package com.example.k8scomp.service;

import com.example.k8scomp.model.User;
import com.example.k8scomp.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CleanupService {

    private final UserRepository userRepository;

    public CleanupService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Runs every hour to check for expired unverified users
    @Scheduled(fixedRate = 3600000)
    public void deleteUnverifiedUsers() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(1);
        
        // Fetch all unverified users created before the cutoff
        List<User> expiredUsers = userRepository.findAll().stream()
                .filter(user -> !user.isVerified() && user.getCreatedAt() != null && user.getCreatedAt().isBefore(cutoff))
                .collect(Collectors.toList());

        if (!expiredUsers.isEmpty()) {
            userRepository.deleteAll(expiredUsers);
            System.out.println("Cleaned up " + expiredUsers.size() + " unverified users.");
        }
    }
}
