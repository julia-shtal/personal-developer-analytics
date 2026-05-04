package com.juliashtal.devanalytics.git.model;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;
import com.juliashtal.devanalytics.user.model.User;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(
        name = "user_repo_registrations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "repo_id"})
)
public class UserRepoRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id")
    private GitRepositoryEntity repository;

    /** The data source through which this subscription was created; null for manual subscriptions. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_source_id")
    private DataSourceConfig dataSourceConfig;
}
