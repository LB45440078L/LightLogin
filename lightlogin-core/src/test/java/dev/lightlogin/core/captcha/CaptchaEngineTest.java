package dev.lightlogin.core.captcha;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptchaEngineTest {

    private static final long NOW = 1_700_000_000_000L;

    private CaptchaEngine arithmetic(int maxAttempts) {
        return new CaptchaEngine(CaptchaEngine.Mode.ARITHMETIC, maxAttempts, 60_000, 0, 16);
    }

    @Test
    @DisplayName("a correct arithmetic answer verifies")
    void arithmeticSuccess() {
        CaptchaEngine engine = arithmetic(3);
        CaptchaChallenge challenge = engine.issue("player-1", NOW);
        CaptchaChallenge.Arithmetic arithmetic = assertInstanceOf(CaptchaChallenge.Arithmetic.class, challenge);

        CaptchaResult result = engine.verify("player-1", String.valueOf(arithmetic.result()), NOW);
        assertInstanceOf(CaptchaResult.Verified.class, result);
        // The challenge is consumed.
        assertSame(CaptchaResult.NO_CHALLENGE, engine.verify("player-1", "1", NOW));
    }

    @Test
    @DisplayName("a wrong answer consumes an attempt and reports the remainder")
    void wrongAnswer() {
        CaptchaEngine engine = arithmetic(3);
        engine.issue("player-2", NOW);

        CaptchaResult first = engine.verify("player-2", "not-a-number", NOW);
        CaptchaResult.WrongAnswer wrong = assertInstanceOf(CaptchaResult.WrongAnswer.class, first);
        assertEquals(2, wrong.attemptsRemaining());

        engine.verify("player-2", "-999999", NOW);
        CaptchaResult third = engine.verify("player-2", "999999999", NOW);
        assertInstanceOf(CaptchaResult.TooManyAttempts.class, third);
    }

    @Test
    @DisplayName("an expired challenge is refused and removed")
    void expiry() {
        CaptchaEngine engine = new CaptchaEngine(CaptchaEngine.Mode.ARITHMETIC, 3, 10_000, 0, 16);
        CaptchaChallenge challenge = engine.issue("player-3", NOW);
        CaptchaChallenge.Arithmetic arithmetic = assertInstanceOf(CaptchaChallenge.Arithmetic.class, challenge);

        CaptchaResult result = engine.verify("player-3", String.valueOf(arithmetic.result()), NOW + 20_000);
        assertInstanceOf(CaptchaResult.Expired.class, result);
        assertEquals(0, engine.size());
    }

    @Test
    @DisplayName("re-issuing within the cooldown returns the same challenge")
    void cooldown() {
        CaptchaEngine engine = new CaptchaEngine(CaptchaEngine.Mode.ARITHMETIC, 3, 60_000, 30_000, 16);
        CaptchaChallenge first = engine.issue("player-4", NOW);
        CaptchaChallenge second = engine.issue("player-4", NOW + 1_000);
        assertSame(first, second, "spamming must not farm a fresh question");

        CaptchaChallenge later = engine.issue("player-4", NOW + 31_000);
        assertNotEquals(first.id(), later.id());
    }

    @Test
    @DisplayName("the proof-of-work challenge accepts a mined nonce and rejects a wrong one")
    void proofOfWork() throws Exception {
        CaptchaEngine engine = new CaptchaEngine(CaptchaEngine.Mode.PROOF_OF_WORK, 2, 60_000, 0, 12);
        CaptchaChallenge challenge = engine.issue("bot", NOW);
        CaptchaChallenge.ProofOfWork pow = assertInstanceOf(CaptchaChallenge.ProofOfWork.class, challenge);

        assertTrue(engine.verify("bot", "0", NOW) instanceof CaptchaResult.WrongAnswer);

        String nonce = mine(pow.seed(), pow.difficultyBits());
        CaptchaResult result = engine.verify("bot", nonce, NOW);
        assertInstanceOf(CaptchaResult.Verified.class, result);
    }

    @Test
    @DisplayName("proof-of-work difficulty is validated")
    void powValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> new CaptchaChallenge.ProofOfWork("id", "seed", 4, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> new CaptchaChallenge.ProofOfWork("id", "seed", 40, NOW));
    }

    @Test
    @DisplayName("leading zero bits are counted correctly")
    void leadingZeroBits() {
        assertEquals(8, CaptchaChallenge.ProofOfWork.leadingZeroBits(new byte[]{0, (byte) 0x80}));
        assertEquals(0, CaptchaChallenge.ProofOfWork.leadingZeroBits(new byte[]{(byte) 0xFF}));
        // 0x00 0x00 0x01 = 16 zero bits, then 0x01 has 7 leading zero bits within its byte.
        assertEquals(23, CaptchaChallenge.ProofOfWork.leadingZeroBits(new byte[]{0, 0, 1}));
    }

    @Test
    @DisplayName("expired challenges are swept")
    void sweep() {
        CaptchaEngine engine = new CaptchaEngine(CaptchaEngine.Mode.ARITHMETIC, 3, 1_000, 0, 16);
        engine.issue("a", NOW);
        engine.issue("b", NOW);
        assertEquals(2, engine.size());
        engine.sweep(NOW + 2_000);
        assertEquals(0, engine.size());
    }

    private static String mine(String seed, int difficultyBits) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (long nonce = 0; nonce < 50_000_000L; nonce++) {
            digest.reset();
            digest.update(seed.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            byte[] hash = digest.digest(Long.toString(nonce).getBytes(StandardCharsets.UTF_8));
            if (CaptchaChallenge.ProofOfWork.leadingZeroBits(hash) >= difficultyBits) {
                return Long.toString(nonce);
            }
        }
        throw new IllegalStateException("no nonce found");
    }
}