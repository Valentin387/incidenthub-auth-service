package com.incidenthub.auth.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.incidenthub.auth.TestcontainersConfiguration;
import com.incidenthub.auth.WebClientTestConfig;
import com.incidenthub.auth.WireMockConfig;
import com.incidenthub.auth.dto.LoginRequestDTO;
import com.incidenthub.auth.dto.UserDTO;
import com.incidenthub.auth.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({
        WireMockConfig.class,
        TestcontainersConfiguration.class,
        WebClientTestConfig.class
})  // Add TestcontainersConfiguration

class AuthServiceIntegrationTest {
    // You can now inject the WireMockServer bean
    @Autowired
    private WireMockServer wireMockServer;
    @Autowired
    private AuthService authService;  // Change from public to private and add @Autowired
    @Autowired
    private R2dbcEntityTemplate r2dbcEntityTemplate;  // Add this field
    private UserDTO userDTO;
    private LoginRequestDTO loginRequestDTO;
    private User user;

    private void initializeTestData() {
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
        user.setPassword("$2a$10$exampleHashedPassword123");
        user.setRole("OPERATOR");
        user.setCreatedAt(Instant.now());
    }

    @BeforeEach
    void setUp() {
        initializeTestData();
        // Verify WireMock is running on the expected port
        assertThat(wireMockServer.port()).isEqualTo(8090);

    }

    @AfterEach
    void tearDown() {
        wireMockServer.resetAll();

    }

    @Test
    void wireMockIsRunning() {
        assertThat(wireMockServer.isRunning()).isTrue();
        assertThat(wireMockServer.port()).isGreaterThan(0);
    }

    @Test
    void databaseConnectionTest() {
        assertThat(r2dbcEntityTemplate).isNotNull();
        StepVerifier.create(r2dbcEntityTemplate.getDatabaseClient().sql("SELECT 1").fetch().all())
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void registerSuccess() {
        // Create expected response JSON
        String responseJson = """
        {
            "id": "%s",
            "username": "testuser",
            "email": "test@example.com",
            "role": "OPERATOR",
            "createdAt": "%s"
        }
        """.formatted(user.getId(), user.getCreatedAt().toString());

        // Set up WireMock with more specific matching
        wireMockServer.stubFor(post(urlEqualTo("/api/users"))
            .withHeader("Content-Type", containing("application/json"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .withBody(responseJson)));

        // Act & Assert
        StepVerifier.create(authService.register(userDTO))
            .expectNextMatches(dto -> {
                assertThat(dto.getUsername()).isEqualTo("testuser");
                assertThat(dto.getEmail()).isEqualTo("test@example.com");
                assertThat(dto.getRole()).isEqualTo("OPERATOR");
                assertThat(dto.getPassword()).isNull();
                return true;
            })
            .verifyComplete();

        // Verify that the request was made
        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/users")));
    }

    @Test
    void registerUserServiceFailure() {
        // Arrange: Mock User Service failure
        wireMockServer.stubFor(post(urlEqualTo("/api/users"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.BAD_REQUEST.value())
                        .withBody("{\"error\": \"Username already exists\"}")));

        // Act & Assert
        StepVerifier.create(authService.register(userDTO))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().contains("User not found")
                )
                .verify();
    }

    @Test
    void loginSuccess() {
        // Arrange: Mock User Service GET /api/users/username/{username}
        wireMockServer.stubFor(get(urlEqualTo("/api/users/username/testuser"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{ \"id\": \"" + user.getId() + "\", \"username\": \"testuser\", \"email\": \"test@example.com\", \"password\": \"$2a$10$exampleHashedPassword123\", \"role\": \"OPERATOR\", \"createdAt\": \"" + user.getCreatedAt() + "\" }")));

        // Act & Assert
        StepVerifier.create(authService.login(loginRequestDTO))
                .expectNextMatches(response -> {
                    assertThat(response.getToken()).isNotEmpty();
                    return true;
                })
                .verifyComplete();
    }

    @Test
    void loginInvalidUsername() {
        // Arrange: Mock User Service 404
        wireMockServer.stubFor(get(urlEqualTo("/api/users/username/testuser"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.NOT_FOUND.value())
                        .withBody("{\"error\": \"User not found\"}")));

        // Act & Assert
        StepVerifier.create(authService.login(loginRequestDTO))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().equals("User not found")
                )
                .verify();
    }

    @Test
    void loginInvalidPassword() {
        // Arrange: Mock User Service GET with valid user
        wireMockServer.stubFor(get(urlEqualTo("/api/users/username/testuser"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{ \"id\": \"" + user.getId() + "\", \"username\": \"testuser\", \"email\": \"test@example.com\", \"password\": \"$2a$10$exampleHashedPassword123\", \"role\": \"OPERATOR\", \"createdAt\": \"" + user.getCreatedAt() + "\" }")));

        // Modify login request to use wrong password
        loginRequestDTO.setPassword("wrongpassword");

        // Act & Assert
        StepVerifier.create(authService.login(loginRequestDTO))
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().equals("Invalid credentials")
                )
                .verify();
    }

    @Test
    void registerInvalidRole() {
        // Arrange
        userDTO.setRole("INVALID");

        // Act & Assert
        StepVerifier.create(authService.register(userDTO))
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalArgumentException &&
                                throwable.getMessage().equals("Invalid role")
                )
                .verify();
    }

    @Test
    void loginEmptyPassword() {
        // Arrange
        loginRequestDTO.setPassword("");

        // Act & Assert
        StepVerifier.create(authService.login(loginRequestDTO))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}