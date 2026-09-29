package com.juliashtal.devanalytics.security;

import com.juliashtal.devanalytics.security.model.CustomUserDetails;
import com.juliashtal.devanalytics.user.model.Role;
import com.juliashtal.devanalytics.user.model.User;
import com.juliashtal.devanalytics.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@code currentUser()} must never trust a request-supplied id — it resolves strictly from the
 * security context, which is what makes {@code CheckHelper.currentUser()} the safe alternative
 * the project's RBAC rule requires over a body/param {@code userId}.
 */
@ExtendWith(MockitoExtension.class)
class CheckHelperTest {

    @Mock UserRepository userRepository;
    @InjectMocks CheckHelper checkHelper;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUser_authenticated_resolvesByIdFromTheSecurityContext() {
        User principalUser = new User();
        principalUser.setId(1L);
        principalUser.setRole(Role.DEVELOPER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new CustomUserDetails(principalUser), null));

        User referenced = new User();
        referenced.setId(1L);
        when(userRepository.getReferenceById(1L)).thenReturn(referenced);

        assertThat(checkHelper.currentUser()).isSameAs(referenced);
    }
}
