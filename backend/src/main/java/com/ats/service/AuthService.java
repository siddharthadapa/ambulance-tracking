package com.ats.service;

import com.ats.dto.AuthRequest;
import com.ats.dto.AuthResponse;
import com.ats.dto.RegisterRequest;
import com.ats.model.Ambulance;
import com.ats.model.User;
import com.ats.repository.AmbulanceRepository;
import com.ats.repository.UserRepository;
import com.ats.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final AmbulanceRepository ambulanceRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        User.Role role = req.resolveRole();

        User user = User.builder()
                .fullName(req.getFullName())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .phone(req.getPhone())
                .role(role)
                .enabled(true)
                .build();

        user = userRepository.save(user);

        if (role == User.Role.DRIVER) {
            if (req.getVehicleNumber() == null || req.getVehicleNumber().isBlank()) {
                throw new IllegalArgumentException("vehicleNumber is required for DRIVER registration");
            }
            Ambulance ambulance = Ambulance.builder()
                    .vehicleNumber(req.getVehicleNumber())
                    .driver(user)
                    .currentLat(0.0)
                    .currentLng(0.0)
                    .status(Ambulance.Status.OFFLINE)
                    .build();
            ambulanceRepository.save(ambulance);
        }

        return buildAuthResponse(user);
    }

    public AuthResponse login(AuthRequest req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword()));

        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities("ROLE_" + user.getRole().name())
                .build();

        String token = jwtUtil.generateToken(userDetails, user.getId(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }
}
