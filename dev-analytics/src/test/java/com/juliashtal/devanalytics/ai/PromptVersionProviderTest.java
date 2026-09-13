package com.juliashtal.devanalytics.ai;

import com.juliashtal.devanalytics.ai.service.PromptVersionProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that a stored summary can be traced back to the prompt that produced it: the version is a
 * pure function of the prompt text, differs per prompt, and cannot drift without this test failing.
 */
class PromptVersionProviderTest {

    /**
     * Prompt versions as committed. A prompt edit invalidates every result collected before it, so
     * changing these values is a deliberate act, not a build fix.
     */
    private static final String EXPECTED_PERSONAL = "eded29f49ed62b99";
    private static final String EXPECTED_TEAM = "20df573026957922";

    @Test
    void hashFor_committedPromptText_matchesExpectedVersion() {
        PromptVersionProvider provider = new PromptVersionProvider();

        assertThat(provider.hashFor("PERSONAL")).isEqualTo(EXPECTED_PERSONAL);
        assertThat(provider.hashFor("TEAM")).isEqualTo(EXPECTED_TEAM);
    }

    @Test
    void hashFor_twoConstructions_producesSameVersion() {
        assertThat(new PromptVersionProvider().hashFor("PERSONAL"))
                .isEqualTo(new PromptVersionProvider().hashFor("PERSONAL"));
        assertThat(new PromptVersionProvider().hashFor("TEAM"))
                .isEqualTo(new PromptVersionProvider().hashFor("TEAM"));
    }

    @Test
    void hashFor_personalAndTeam_differ() {
        PromptVersionProvider provider = new PromptVersionProvider();

        assertThat(provider.hashFor("PERSONAL")).isNotEqualTo(provider.hashFor("TEAM"));
    }

    @Test
    void hashFor_repositoryScope_usesPersonalVersion() {
        PromptVersionProvider provider = new PromptVersionProvider();

        assertThat(provider.hashFor("REPOSITORY")).isEqualTo(provider.hashFor("PERSONAL"));
    }

    @Test
    void hashFor_anyScope_isSixteenHexCharacters() {
        PromptVersionProvider provider = new PromptVersionProvider();

        assertThat(provider.hashFor("PERSONAL")).matches("[0-9a-f]{16}");
        assertThat(provider.hashFor("TEAM")).matches("[0-9a-f]{16}");
    }
}
