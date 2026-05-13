package com.patchmgmt.dto;
import com.patchmgmt.enums.UserRole;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class UserRegistrationDto {
    @NotBlank @Size(min=3, max=50)  private String username;
    
    @Pattern(
        regexp = "^$|^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
        message = "Must be a valid email address"
    )
    private String email;
    
    @NotBlank @Size(min=6, message="Password must be at least 6 characters") private String password;
    private String fullName;                                    // optional
    private UserRole role = UserRole.ROLE_USER;
}
