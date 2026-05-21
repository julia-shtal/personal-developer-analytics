package com.juliashtal.devanalytics.github.repository;

import com.juliashtal.devanalytics.github.model.GitHubPrReviewEntity;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.metrics.model.PrReviewTimestampProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GitHubPrReviewRepository extends JpaRepository<GitHubPrReviewEntity, Long> {

    void deleteAllByPullRequest(GitHubPullRequestEntity pullRequest);
    void deleteAllByPullRequestIn(List<GitHubPullRequestEntity> pullRequests);

    /**
     * Returns [prId, firstReviewSubmittedAt] for each PR that has at least one review.
     * Used by MetricsService to calculate REVIEW_RESPONSE_TIME_HOURS_MEDIAN.
     */
    @Query("""
            SELECT r.pullRequest.id as prId,
                   MIN(r.submittedAt) as reviewedAt
            FROM GitHubPrReviewEntity r
            WHERE r.pullRequest.id IN :prIds
            GROUP BY r.pullRequest.id
            """)
    List<PrReviewTimestampProjection> findFirstReviewTimestampsByPrIds(@Param("prIds") List<Long> prIds);
}
