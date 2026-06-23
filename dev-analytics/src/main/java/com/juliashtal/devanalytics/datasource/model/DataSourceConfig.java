package com.juliashtal.devanalytics.datasource.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.juliashtal.devanalytics.user.model.Team;
import com.juliashtal.devanalytics.user.model.User;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "data_source_configs")
public class DataSourceConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    @JsonIgnore
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DataSourceType type;

    // clear name: "Local Git", "GitHub personal", "Jira Work"
    @Column(nullable = false)
    private String name;

    // for HTTP‑sources: base URL (https://github.com, https://yourcompany.atlassian.net)
    private String baseUrl;

    // for local git: path to repo
    private String path;

    // token/key (encrypted)
    @Column(name = "api_token_encrypted")
    private String apiTokenEncrypted;

    private boolean enabled = true;
    private Instant lastSuccessSync;
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
