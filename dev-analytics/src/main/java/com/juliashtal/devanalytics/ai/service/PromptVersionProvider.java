package com.juliashtal.devanalytics.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Derives the prompt version stamped on every generated summary from the prompt text itself.
 * <p>Editing a prompt therefore changes the version with no separate bookkeeping step, which is
 * what keeps a stored summary attributable to the instruction that produced it.</p>
 */
@Component
@Slf4j
public class PromptVersionProvider {

    /** Only TEAM takes the team prompt; PERSONAL and REPOSITORY share the personal one. */
    private static final String TEAM_SCOPE = "TEAM";

    /** Hex characters kept from the digest — more precision than the identity needs is waste. */
    private static final int VERSION_LENGTH = 16;

    private final String personalHash;
    private final String teamHash;

    public PromptVersionProvider() {
        this.personalHash = version(SystemPrompts.PERSONAL);
        this.teamHash = version(SystemPrompts.TEAM);
        log.info("Prompt versions: personal={}, team={}", personalHash, teamHash);
    }

    /** @param scope one of PERSONAL, REPOSITORY, TEAM. */
    public String hashFor(String scope) {
        return TEAM_SCOPE.equals(scope) ? teamHash : personalHash;
    }

    private static String version(String prompt) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(prompt.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, VERSION_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required to version prompts", e);
        }
    }
}
