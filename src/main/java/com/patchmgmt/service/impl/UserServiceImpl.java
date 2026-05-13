package com.patchmgmt.service.impl;

import com.patchmgmt.dto.UserRegistrationDto;
import com.patchmgmt.entity.AppUser;
import com.patchmgmt.repository.UserRepository;
import com.patchmgmt.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public AppUser register(UserRegistrationDto dto) {
        if (userRepository.existsByUsername(dto.getUsername()))
            throw new IllegalArgumentException("Username already taken: " + dto.getUsername());

        // Determine email: use provided or fallback to a dummy to satisfy the database NOT NULL constraint
        String email = (dto.getEmail() != null && !dto.getEmail().isBlank()) 
            ? dto.getEmail() 
            : dto.getUsername() + "@unprovided.local";
            
        if (userRepository.existsByEmail(email))
            throw new IllegalArgumentException("Email already registered: " + email);

        AppUser user = AppUser.builder()
            .username(dto.getUsername())
            .email(email)
            .password(passwordEncoder.encode(dto.getPassword()))
            .fullName(dto.getFullName())
            .role(dto.getRole() != null ? dto.getRole() : com.patchmgmt.enums.UserRole.ROLE_USER)
            .enabled(true)
            .build();
        return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AppUser> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AppUser> findById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppUser> findAll() {
        return userRepository.findAll();
    }

    @Override
    public AppUser update(Long id, UserRegistrationDto dto) {
        AppUser user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
        user.setFullName(dto.getFullName());
        user.setEmail(dto.getEmail());
        user.setRole(dto.getRole());
        if (dto.getPassword() != null && !dto.getPassword().isBlank())
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        return userRepository.save(user);
    }

    @Override
    public void toggleEnabled(Long id) {
        AppUser user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found"));
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
    }

    @Override
    public void delete(Long id) {
        userRepository.deleteById(id);
    }

    @Override
    public void updateLastLogin(String username) {
        userRepository.findByUsername(username).ifPresent(u -> {
            u.setLastLogin(LocalDateTime.now());
            userRepository.save(u);
        });
    }
}
