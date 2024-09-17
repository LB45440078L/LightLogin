package top.cmarco.lightlogin.command.verify;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

public final class VerificationData {

    private static final ThreadLocalRandom THREAD_LOCAL_RANDOM = ThreadLocalRandom.current();

    private final String mathFormula;
    private final int answer;

    public VerificationData(String mathFormula, int answer) {
        this.mathFormula = mathFormula;
        this.answer = answer;
    }

    public static VerificationData generateRandom() {
        int a = THREAD_LOCAL_RANDOM.nextInt(1, 100);
        int b = THREAD_LOCAL_RANDOM.nextInt(1, 100);
        boolean isSum = THREAD_LOCAL_RANDOM.nextBoolean();
        String formula;
        if (isSum) {
            formula = String.format("%d + %d", a, b);
            int c = a + b;
            return new VerificationData(formula, c);
        } else {
            int max = Math.max(a, b);
            int min = Math.min(a, b);
            formula = String.format("%d - %d", max, min);
            return new VerificationData(formula, max-min);
        }
    }

    public int getAnswer() {
        return answer;
    }

    @NotNull
    public String getMathFormula() {
        return mathFormula;
    }
}
