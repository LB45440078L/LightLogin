package dev.lightlogin.paper.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleBannerAndFilterTest {

    private static final Pattern BLOCK_OR_BOX = Pattern.compile(
            "[\\p{InBlock_Elements}\\p{InBox_Drawing}]");

    @Test
    @DisplayName("the banner art is plain ASCII so no console renders it as mojibake")
    void bannerIsAscii() {
        for (String line : ConsoleBanner.bannerArt()) {
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                assertTrue(c >= 0x20 && c < 0x7F,
                        "non-ASCII character '" + c + "' in banner line: " + line);
            }
            assertFalse(BLOCK_OR_BOX.matcher(line).find(),
                    "block/box drawing character in banner line: " + line);
        }
    }

    @Test
    @DisplayName("every banner line is the same width, so the art cannot shear")
    void bannerIsRectangular() {
        int expected = -1;
        for (String line : ConsoleBanner.bannerArt()) {
            if (line.isBlank()) {
                continue;
            }
            if (expected < 0) {
                expected = line.length();
            }
            assertEquals(expected, line.length(),
                    "banner line is " + line.length() + " wide but the block is " + expected
                            + " wide, so the art will shear: " + line);
        }

        // The regression this guards: a Java literal written with doubled backslashes renders two
        // characters where the art needs one, which pushed the descender of the "g" out of line.
        for (String line : ConsoleBanner.bannerArt()) {
            assertFalse(line.contains("\\\\"),
                    "the art contains a doubled backslash, which shifts the glyphs: " + line);
        }
        // The "g" descender must be present and single-stroked, or the wordmark is not the intended one.
        assertTrue(java.util.Arrays.stream(ConsoleBanner.bannerArt()).anyMatch(l -> l.contains("|___/")),
                "the descender rows of the wordmark are missing");
    }

    @Test
    @DisplayName("credential-bearing command lines are dropped from the log")
    void credentialLinesDropped() {
        assertTrue(SecretLogFilter.wouldRedact("/login MySecret123!"));
        assertTrue(SecretLogFilter.wouldRedact("Steve issued server command: /login hunter2"));
        assertTrue(SecretLogFilter.wouldRedact("/register abc def"));
        assertTrue(SecretLogFilter.wouldRedact("/changepassword old new"));
        assertTrue(SecretLogFilter.wouldRedact("Player issued server command: /resetpassword steve"));
        assertTrue(SecretLogFilter.wouldRedact("/verify 42"));
    }

    @Test
    @DisplayName("ordinary lines are not swallowed")
    void ordinaryLinesKept() {
        assertFalse(SecretLogFilter.wouldRedact("Steve joined the game"));
        assertFalse(SecretLogFilter.wouldRedact("Steve left the game"));
        assertFalse(SecretLogFilter.wouldRedact("Preparing spawn area: 12%"));
        // A bare command with no argument is not a credential leak.
        assertFalse(SecretLogFilter.wouldRedact("/login"));
        assertFalse(SecretLogFilter.wouldRedact("Something about a password policy"));
    }

    @Test
    @DisplayName("a null or empty line is never dropped")
    void nullSafe() {
        assertFalse(SecretLogFilter.wouldRedact(null));
        assertFalse(SecretLogFilter.wouldRedact(""));
    }
}