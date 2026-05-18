package com.juliashtal.devanalytics.jira;

import com.juliashtal.devanalytics.jira.model.JiraProjectEntity;
import com.juliashtal.devanalytics.jira.model.UserProjectRegistration;
import com.juliashtal.devanalytics.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserProjectRegistrationRepository extends JpaRepository<UserProjectRegistration, Long> {
    boolean existsByUserAndProject(User user, JiraProjectEntity project);
    Optional<UserProjectRegistration> findByUserAndProject(User user, JiraProjectEntity project);
    List<UserProjectRegistration> findAllByUser(User user);
}
