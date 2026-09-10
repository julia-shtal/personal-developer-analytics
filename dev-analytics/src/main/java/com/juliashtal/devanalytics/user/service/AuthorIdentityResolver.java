package com.juliashtal.devanalytics.user.service;

import com.juliashtal.devanalytics.user.model.AuthorIdentity;
import com.juliashtal.devanalytics.user.model.User;

/**
 * Resolves a user to the identifiers their records are attributed by.
 *
 * <p>The single entry point from {@code metrics} into {@code user}: calculators take an
 * {@link AuthorIdentity} and never read identity fields off {@link User} themselves, so
 * normalisation happens in exactly one place and no calculator can reintroduce name matching.
 */
public interface AuthorIdentityResolver {
    AuthorIdentity resolve(User user);
}
