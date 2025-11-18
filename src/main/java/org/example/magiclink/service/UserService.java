package org.example.magiclink.service;

import lombok.RequiredArgsConstructor;
import org.example.magiclink.entity.User;
import org.example.magiclink.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    public User findOrCreate(String email) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setEmailVerified(false);
            u.setEnabled(true);
            u.setCreatedAt(LocalDateTime.now());
            return userRepository.save(u);
        });
    }

    public User findOrCreateFromGoogle(String email, String googleId) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setGoogleId(googleId);
            u.setEmailVerified(true);
            u.setEnabled(true);
            u.setCreatedAt(LocalDateTime.now());
            u.setLastLoginAt(LocalDateTime.now());
            return userRepository.save(u);
        });
    }

    public void updateLastLogin(String email) {
        userRepository.findByEmail(email).ifPresent(u -> {
            u.setLastLoginAt(LocalDateTime.now());
            userRepository.save(u);
        });
    }

    public boolean hasGoogleAccount(String email) {
        return userRepository.findByEmail(email)
                .map(u -> u.getGoogleId() != null)
                .orElse(false);
    }
}