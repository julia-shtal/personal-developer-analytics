package com.juliashtal.devanalytics.user.repository;

import com.juliashtal.devanalytics.user.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByManagerId(Long managerId);

    List<Team> findByMembersId(Long userId);

    boolean existsByIdAndManagerId(Long teamId, Long managerId);

    boolean existsByIdAndMembersId(Long teamId, Long userId);

    @Query("SELECT tm.id FROM Team t JOIN t.members tm WHERE t.manager.id = :managerId")
    List<Long> findTeamMemberIdsByManagerId(Long managerId);

    /**
     * Returns true if u1 and u2 share at least one team (either as manager or member).
     * Used to gate direct messaging — users may only DM teammates.
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM teams t
                WHERE (
                    t.manager_id = :u1 OR
                    EXISTS (SELECT 1 FROM team_members tm WHERE tm.team_id = t.id AND tm.user_id = :u1)
                ) AND (
                    t.manager_id = :u2 OR
                    EXISTS (SELECT 1 FROM team_members tm WHERE tm.team_id = t.id AND tm.user_id = :u2)
                )
            )
            """, nativeQuery = true)
    boolean shareTeam(@Param("u1") Long u1, @Param("u2") Long u2);
}
