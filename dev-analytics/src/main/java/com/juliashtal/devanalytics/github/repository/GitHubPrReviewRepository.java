package com.juliashtal.devanalytics.github.repository;

import com.juliashtal.devanalytics.github.model.GitHubPrReviewEntity;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.metrics.model.PrReviewTimestampProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /**
     * Counts distinct PRs reviewed by the given GitHub login within the time window,
     * scoped to the given repository IDs, excluding self-reviews.
     *
     * <p>Bot reviewer accounts cannot appear because the query is already scoped to the
     * specific user's GitHub login, which is a registered human account. Ingestion
     * preserves raw review records for audit.</p>
     *
     * <p>Uses distinct PR ID to avoid counting multiple reviews on the same PR. The {@code to}
     * bound is exclusive so adjacent calculation windows do not double-count.</p>
     */
    @Query("""
            SELECT COUNT(DISTINCT r.pullRequest.id)
            FROM GitHubPrReviewEntity r
            WHERE r.reviewerLogin = :reviewerLogin
            AND r.pullRequest.repository.id IN :repoIds
            AND r.submittedAt >= :from
            AND r.submittedAt < :to
            AND r.pullRequest.authorLogin <> :reviewerLogin
            """)
    long countDistinctPrsReviewedByUser(
            @Param("reviewerLogin") String reviewerLogin,
            @Param("repoIds") List<Long> repoIds,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
