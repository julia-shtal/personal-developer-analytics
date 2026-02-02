package com.juliashtal.devanalytics.git.repository;

import com.juliashtal.devanalytics.git.model.GitCommitEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GitCommitEntityRepository extends JpaRepository<GitCommitEntity, Long> {
    Optional<GitCommitEntity> findByHash(String hash);
    long countByRepositoryId(Long repositoryId);
    Page<GitCommitEntity> findByRepositoryIdOrderByAuthorDateDesc(Long repositoryId, Pageable pageable);
}

