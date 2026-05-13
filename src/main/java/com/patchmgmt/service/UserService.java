package com.patchmgmt.service;

import com.patchmgmt.dto.UserRegistrationDto;
import com.patchmgmt.entity.AppUser;
import java.util.List;
import java.util.Optional;

public interface UserService {
    AppUser register(UserRegistrationDto dto);
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findById(Long id);
    List<AppUser> findAll();
    AppUser update(Long id, UserRegistrationDto dto);
    void toggleEnabled(Long id);
    void delete(Long id);
    void updateLastLogin(String username);
}
