package com.incidenthub.auth.service;

import com.incidenthub.auth.dto.LoginRequestDTO;
import com.incidenthub.auth.dto.LoginResponseDTO;
import com.incidenthub.auth.dto.UserDTO;
import com.incidenthub.auth.model.User;
import com.incidenthub.auth.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.Builder webClientBuilder; // Add this mock

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private AuthService authService;

    private UserDTO userDTO;
    private LoginRequestDTO loginRequestDTO;
    private User user;

    @BeforeEach
    void setUp() {
        // Initialize test data
        userDTO = new UserDTO();
        userDTO.setUsername("testuser");
        userDTO.setEmail("test@example.com");
        userDTO.setPassword("password123");
        userDTO.setRole("OPERATOR");

        loginRequestDTO = new LoginRequestDTO();
        loginRequestDTO.setUsername("testuser");
        loginRequestDTO.setPassword("password123");

        user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPassword("encodedPassword");
        user.setRole("OPERATOR");
        user.setCreatedAt(Instant.now());

        // Mock WebClient.Builder behavior
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        // Create AuthService with the properly mocked builder
        authService = new AuthService(jwtUtil, passwordEncoder, webClientBuilder);
    }

    @Test
    void register_success() {
        // Arrange
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/users")).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.bodyValue(any(User.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(User.class)).thenReturn(Mono.just(user));

        // Act & Assert
        StepVerifier.create(authService.register(userDTO))
                .expectNextMatches(dto ->
                        dto.getUsername().equals("testuser") &&
                                dto.getEmail().equals("test@example.com") &&
                                dto.getRole().equals("OPERATOR") &&
                                dto.getPassword() == null // Password should not be returned
                )
                .verifyComplete();

        verify(passwordEncoder).encode("password123");
        verify(webClient).post();
    }

    @Test
    void register_userServiceFailure() {
        // Arrange
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/users")).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.bodyValue(any(User.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(User.class)).thenReturn(Mono.error(new RuntimeException("User Service error")));

        // Act & Assert
        StepVerifier.create(authService.register(userDTO))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().equals("User Service error")
                )
                .verify();

        verify(passwordEncoder).encode("password123");
        verify(webClient).post();
    }

    @Test
    void login_success() {
        // Arrange
        String token = "jwt.token.here";
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(
                eq("/api/users/username/{username}"),
                eq("testuser")
        )).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        // Add this line to mock onStatus
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(User.class)).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(true);
        when(jwtUtil.generateToken(user.getId(), "testuser", "OPERATOR")).thenReturn(token);

        // Act & Assert
        StepVerifier.create(authService.login(loginRequestDTO))
                .expectNextMatches(response ->
                        response.getToken().equals(token)
                )
                .verifyComplete();

        verify(passwordEncoder).matches("password123", "encodedPassword");
        verify(jwtUtil).generateToken(user.getId(), "testuser", "OPERATOR");
        verify(webClient).get();
    }

    @Test
    void login_invalidUsername() {
        // Arrange
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(
                eq("/api/users/username/{username}"),
                eq("testuser")
        )).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        // Add this line to mock onStatus
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(User.class)).thenReturn(Mono.error(new RuntimeException("User not found")));

        // Act & Assert
        StepVerifier.create(authService.login(loginRequestDTO))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().equals("User not found")
                )
                .verify();

        verify(webClient).get();
        verifyNoInteractions(passwordEncoder, jwtUtil);
    }

    @Test
    void login_invalidPassword() {
        // Arrange
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(
                eq("/api/users/username/{username}"),
                eq("testuser")
        )).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        // Add this line to mock onStatus
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(User.class)).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("password123", "encodedPassword")).thenReturn(false);

        // Act & Assert
        StepVerifier.create(authService.login(loginRequestDTO))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().equals("Invalid credentials")
                )
                .verify();

        verify(passwordEncoder).matches("password123", "encodedPassword");
        verify(webClient).get();
        verifyNoInteractions(jwtUtil);
    }
}