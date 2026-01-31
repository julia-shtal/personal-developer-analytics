package com.juliashtal.devanalytics.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "data_source_configs")
public class DataSourceConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private DataSourceType type;

    private String name;
    private String baseUrl; // https://github.com/org/repo, https://yourcompany.atlassian.net
    private String path; // /path/to/local/repo
    @Column(name = "api_token")
    private String apiTokenEncrypted; // Jasypt
    private LocalDateTime lastSuccessSync;
}
