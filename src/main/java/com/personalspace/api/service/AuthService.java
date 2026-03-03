package com.personalspace.api.service;

import com.personalspace.api.dto.request.LoginRequest;
import com.personalspace.api.dto.request.RefreshTokenRequest;
import com.personalspace.api.dto.request.SignupRequest;
import com.personalspace.api.dto.response.AuthResponse;
import com.personalspace.api.exception.EmailAlreadyExistsException;
import com.personalspace.api.exception.RefreshTokenException;
import com.personalspace.api.model.entity.RefreshToken;
import com.personalspace.api.model.entity.User;
import com.personalspace.api.model.enums.Role;
import com.personalspace.api.repository.RefreshTokenRepository;
import com.personalspace.api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final long refreshTokenExpiration;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user.getEmail());
        String refreshToken = createRefreshToken(user).getToken();

        return new AuthResponse(accessToken, refreshToken, jwtService.getAccessTokenExpiration());
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow();

        String accessToken = jwtService.generateAccessToken(user.getEmail());
        String refreshToken = createRefreshToken(user).getToken();

        return new AuthResponse(accessToken, refreshToken, jwtService.getAccessTokenExpiration());
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new RefreshTokenException("Invalid refresh token"));

        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new RefreshTokenException("Refresh token has expired");
        }

        User user = refreshToken.getUser();

        // Rotate: delete old, create new
        refreshTokenRepository.delete(refreshToken);

        String accessToken = jwtService.generateAccessToken(user.getEmail());
        String newRefreshToken = createRefreshToken(user).getToken();

        return new AuthResponse(accessToken, newRefreshToken, jwtService.getAccessTokenExpiration());
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenRepository.deleteByToken(request.refreshToken());
    }

    private RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenExpiration));
        return refreshTokenRepository.save(refreshToken);
    }
}
