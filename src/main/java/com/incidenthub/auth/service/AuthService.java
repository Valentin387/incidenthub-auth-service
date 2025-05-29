package com.incidenthub.auth.service;

import com.incidenthub.auth.dto.LoginRequestDTO;
import com.incidenthub.auth.dto.LoginResponseDTO;
import com.incidenthub.auth.dto.UserDTO;
import com.incidenthub.auth.model.User;
import com.incidenthub.auth.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import org.springframework.beans.factory.annotation.Value;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

@Service
public class AuthService {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final WebClient webClient;

    public AuthService(JwtUtil jwtUtil, PasswordEncoder passwordEncoder, WebClient.Builder webClientBuilder,
                       @Value("${incidenthub.user-service.url}") String userServiceUrl) {
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.webClient = webClientBuilder.baseUrl(userServiceUrl).build();
    }

    public Mono<UserDTO> register(UserDTO userDTO) {
        if (userDTO.getUsername() == null || userDTO.getEmail() == null || userDTO.getPassword() == null) {
            return Mono.error(new IllegalArgumentException("Invalid user data"));
        }
        if (!List.of("ADMIN", "ANALYST", "OPERATOR").contains(userDTO.getRole())) {
            return Mono.error(new IllegalArgumentException("Invalid role"));
        }

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(userDTO.getUsername());
        user.setEmail(userDTO.getEmail());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setRole(userDTO.getRole());
        user.setCreatedAt(Instant.now());

        return webClient.post()
                .uri("/api/users")
                .bodyValue(user)
                .retrieve()
                .bodyToMono(User.class)
                .map(savedUser -> {
                    UserDTO responseDTO = new UserDTO();
                    responseDTO.setUsername(savedUser.getUsername());
                    responseDTO.setEmail(savedUser.getEmail());
                    responseDTO.setRole(savedUser.getRole());
                    return responseDTO;
                });
    }

    public Mono<LoginResponseDTO> login(LoginRequestDTO request) {
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Password cannot be empty"));
        }

        return webClient.get()
                .uri("/api/users/username/{username}", request.getUsername())
                .retrieve()
                .onStatus(status -> status.isError(), response -> Mono.error(new RuntimeException("User not found")))
                .bodyToMono(User.class)
                .flatMap(user -> {
                    if (passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
                        LoginResponseDTO response = new LoginResponseDTO();
                        response.setToken(token);
                        return Mono.just(response);
                    }
                    return Mono.error(new RuntimeException("Invalid credentials"));
                });
    }
}