package com.incidenthub.auth.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table("users")
public class User {
    @Id
    private UUID id;
    private String username;
    private String email;
    private String password;
    private String role; // ADMIN, ANALYST, OPERATOR
    private Instant createdAt;
}