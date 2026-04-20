package com.juliashtal.devanalytics.user.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String email;
    private String passwordHash;
    private String timezone = "Europe/Berlin";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.DEVELOPER;

    @Column(nullable = false)
    private int tokenVersion = 0;

    // GitHub username — required for attributing PRs from team-scoped repos
    private String githubLogin;
}
