package com.juliashtal.devanalytics.git.repository;

import com.juliashtal.devanalytics.git.model.UserRepoRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRepoRegistrationRepository extends JpaRepository<UserRepoRegistration, Long> {

    boolean existsByUserIdAndRepositoryId(Long userId, Long repositoryId);

    @Query("SELECT urr.repository.id FROM UserRepoRegistration urr WHERE urr.user.id = :userId")
    List<Long> findRepoIdsByUserId(@Param("userId") Long userId);
}
