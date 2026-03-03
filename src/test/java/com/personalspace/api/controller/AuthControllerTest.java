package com.personalspace.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalspace.api.dto.request.LoginRequest;
import com.personalspace.api.dto.request.SignupRequest;
import com.personalspace.api.dto.response.AuthResponse;
import com.personalspace.api.exception.EmailAlreadyExistsException;
import com.personalspace.api.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AuthController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                ServletWebSecurityAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.personalspace\\.api\\.security\\..*")
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void signup_shouldReturn201_whenRequestIsValid() throws Exception {
        SignupRequest request = new SignupRequest("Test", "test@test.com", "password123");
        AuthResponse authResponse = new AuthResponse("access-token", "refresh-token", 900000L);

        when(authService.signup(any(SignupRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void signup_shouldReturn400_whenValidationFails() throws Exception {
        SignupRequest request = new SignupRequest("", "invalid", "short");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signup_shouldReturn409_whenEmailExists() throws Exception {
        SignupRequest request = new SignupRequest("Test", "test@test.com", "password123");

        when(authService.signup(any(SignupRequest.class)))
                .thenThrow(new EmailAlreadyExistsException("test@test.com"));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void login_shouldReturn200_whenCredentialsAreValid() throws Exception {
        LoginRequest request = new LoginRequest("test@test.com", "password123");
        AuthResponse authResponse = new AuthResponse("access-token", "refresh-token", 900000L);

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }
}
