package com.juliashtal.devanalytics.user.repository;

import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<User> searchByEmailOrUsername(String q);

    @Modifying
    @Query("UPDATE User u SET u.tokenVersion = u.tokenVersion + 1 WHERE u.id = :id")
    void incrementTokenVersion(Long id);

    long countByRole(Role role);
}
