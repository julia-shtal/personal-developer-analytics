package com.juliashtal.devanalytics.user.repository;

import com.juliashtal.devanalytics.user.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByManagerId(Long managerId);

    List<Team> findByMembersId(Long userId);

    boolean existsByIdAndManagerId(Long teamId, Long managerId);

    boolean existsByIdAndMembersId(Long teamId, Long userId);

    @Query("SELECT tm.id FROM Team t JOIN t.members tm WHERE t.manager.id = :managerId")
    List<Long> findTeamMemberIdsByManagerId(Long managerId);
}
