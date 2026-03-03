package com.personalspace.api.service;

import com.personalspace.api.dto.request.LoginRequest;
import com.personalspace.api.dto.request.SignupRequest;
import com.personalspace.api.dto.response.AuthResponse;
import com.personalspace.api.exception.EmailAlreadyExistsException;
import com.personalspace.api.model.entity.RefreshToken;
import com.personalspace.api.model.entity.User;
import com.personalspace.api.model.enums.Role;
import com.personalspace.api.repository.RefreshTokenRepository;
import com.personalspace.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, refreshTokenRepository, passwordEncoder,
                jwtService, authenticationManager, 604800000L);
    }

    @Test
    void signup_shouldReturnAuthResponse_whenEmailIsNew() {
        SignupRequest request = new SignupRequest("Test", "test@test.com", "password123");

        when(userRepository.existsByEmail("test@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(jwtService.generateAccessToken("test@test.com")).thenReturn("access-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(900000L);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh-token");
        refreshToken.setExpiryDate(Instant.now().plusMillis(604800000L));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(refreshToken);

        AuthResponse response = authService.signup(request);

        assertNotNull(response);
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void signup_shouldThrowException_whenEmailExists() {
        SignupRequest request = new SignupRequest("Test", "test@test.com", "password123");
        when(userRepository.existsByEmail("test@test.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.signup(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_shouldReturnAuthResponse_whenCredentialsAreValid() {
        LoginRequest request = new LoginRequest("test@test.com", "password123");

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@test.com");
        user.setRole(Role.USER);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("test@test.com", null));
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken("test@test.com")).thenReturn("access-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(900000L);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh-token");
        refreshToken.setExpiryDate(Instant.now().plusMillis(604800000L));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(refreshToken);

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
    }
}
