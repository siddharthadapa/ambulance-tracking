package com.ats.dto;

import com.ats.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    private String fullName;

    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank
    private String phone;

    @NotBlank
    private String role; // PATIENT, DRIVER, DISPATCHER

    // Required only when role = DRIVER
    private String vehicleNumber;

    public User.Role resolveRole() {
        return User.Role.valueOf(role.trim().toUpperCase());
    }
}
