package com.ats.service;

import com.ats.dto.RegisterRequest;
import com.ats.model.Ambulance;
import com.ats.model.User;
import com.ats.repository.AmbulanceRepository;
import com.ats.repository.UserRepository;
import com.ats.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AmbulanceRepository ambulanceRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest baseRequest(String role) {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Test User");
        req.setEmail("test@example.com");
        req.setPassword("password123");
        req.setPhone("9999999999");
        req.setRole(role);
        return req;
    }

    @BeforeEach
    void setUp() {
        // Only needed by the tests that reach token generation
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        lenient().when(jwtUtil.generateToken(any(UserDetails.class), any(), anyString())).thenReturn("fake-jwt");
    }

    @Test
    void rejectsRegistrationWhenEmailAlreadyExists() {
        RegisterRequest req = baseRequest("PATIENT");
        when(userRepository.existsByEmail(req.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsDriverRegistrationWithoutVehicleNumber() {
        RegisterRequest req = baseRequest("DRIVER"); // vehicleNumber intentionally left null
        when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vehicleNumber");

        verify(ambulanceRepository, never()).save(any());
    }

    @Test
    void createsLinkedAmbulanceWhenDriverRegistersWithVehicleNumber() {
        RegisterRequest req = baseRequest("DRIVER");
        req.setVehicleNumber("AP16-AM-9001");
        when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        authService.register(req);

        verify(ambulanceRepository).save(argThat((Ambulance a) ->
                a.getVehicleNumber().equals("AP16-AM-9001")
                        && a.getStatus() == Ambulance.Status.OFFLINE
                        && a.getDriver() != null));
    }

    @Test
    void doesNotCreateAmbulanceForPatientRegistration() {
        RegisterRequest req = baseRequest("PATIENT");
        when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(2L);
            return u;
        });

        var response = authService.register(req);

        assertThat(response.getRole()).isEqualTo("PATIENT");
        verify(ambulanceRepository, never()).save(any());
    }
}
