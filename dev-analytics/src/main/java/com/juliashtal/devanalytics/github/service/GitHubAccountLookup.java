package com.juliashtal.devanalytics.github.service;

import com.juliashtal.devanalytics.datasource.model.DataSourceConfig;

import java.util.Optional;

/**
 * Resolves a GitHub account to its numeric ID and canonical login.
 *
 * <p>The interface exists so the {@code user} package can link an identity without depending on
 * GitHub transport code: attribution needs the numeric ID, which only GitHub can supply.
 *
 * <p>The ID and the login are always returned together and must be stored together. Storing a
 * login without its ID reintroduces the free-text matching this whole change removes.
 */
public interface GitHubAccountLookup {

    /** A GitHub account: the stable numeric ID, and the login as GitHub currently spells it. */
    record GitHubAccount(long id, String login) {}

    /**
     * {@code GET /users/{login}}. Uses the base URL and token of the user's own GitHub data
     * source when they have one, and falls back to an unauthenticated call to the public API.
     *
     * @return empty when GitHub answers 404, i.e. no such login
     * @throws com.juliashtal.devanalytics.exception.GitHubException on transport failure or any
     *         other non-200 response — a lookup that could not complete must not be mistaken
     *         for a login that does not exist
     */
    Optional<GitHubAccount> findByLogin(Long userId, String login);

    /**
     * {@code GET /user}: the account owning this data source's token. Used to link an identity
     * during the user's own collection run, without asking them to type their login.
     *
     * @throws com.juliashtal.devanalytics.exception.GitHubException if the call fails
     */
    GitHubAccount whoAmI(DataSourceConfig config);
}
