package com.juliashtal.devanalytics.github.repository;

import com.juliashtal.devanalytics.github.model.GitHubPrReviewEntity;
import com.juliashtal.devanalytics.github.model.GitHubPullRequestEntity;
import com.juliashtal.devanalytics.metrics.model.PrReviewTimestampProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data repository for GitHubPrReviewEntity (github_pr_reviews). Supplies first-review timestamps per PR.
 */
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
     * Counts distinct PRs reviewed by the given GitHub account within the time window,
     * scoped to the given repository IDs, excluding self-reviews.
     *
     * <p>Reviewer match and self-review exclusion both compare numeric account IDs, so differing
     * spellings cannot credit a self-review. Distinct PR ID avoids counting two reviews of one PR,
     * and the {@code to} bound is exclusive so adjacent windows do not double-count. Bots cannot
     * appear: the query is scoped to one registered human account.</p>
     */
    @Query("""
            SELECT COUNT(DISTINCT r.pullRequest.id)
            FROM GitHubPrReviewEntity r
            WHERE r.reviewerGithubId = :reviewerGithubId
            AND r.pullRequest.repository.id IN :repoIds
            AND r.submittedAt >= :from
            AND r.submittedAt < :to
            AND r.pullRequest.authorGithubId <> :reviewerGithubId
            """)
    long countDistinctPrsReviewedByUser(
            @Param("reviewerGithubId") Long reviewerGithubId,
            @Param("repoIds") List<Long> repoIds,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
