package com.incidenthub.auth.controller;

import com.incidenthub.auth.dto.LoginRequestDTO;
import com.incidenthub.auth.dto.LoginResponseDTO;
import com.incidenthub.auth.dto.UserDTO;
import com.incidenthub.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Mono<UserDTO> register(@Valid @RequestBody UserDTO userDTO) {
        return authService.register(userDTO);
    }

    @PostMapping("/login")
    public Mono<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return authService.login(request);
    }
}