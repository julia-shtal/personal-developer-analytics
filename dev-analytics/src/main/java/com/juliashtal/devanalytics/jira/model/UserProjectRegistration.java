package com.juliashtal.devanalytics.jira.model;

import com.juliashtal.devanalytics.user.model.User;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(
        name = "user_project_registrations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_project_reg",
                columnNames = {"user_id", "project_id"}
        )
)
/**
 * JPA entity for user_project_registrations. Links a user to a subscribed Jira project.
 */
public class UserProjectRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private JiraProjectEntity project;
}
