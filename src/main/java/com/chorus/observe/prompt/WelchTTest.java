package com.chorus.observe.prompt;

import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Welch's t-test for comparing two independent samples with unequal variances.
 * <p>
 * Used in prompt A/B testing to determine if the difference in mean scores
 * between prompt A and prompt B is statistically significant.
 */
public final class WelchTTest {

    private WelchTTest() {}

    /**
     * Result of Welch's t-test.
     *
     * @param tStatistic   the t-statistic
     * @param df           degrees of freedom (Welch–Satterthwaite)
     * @param pValue       two-tailed p-value
     * @param meanA        sample mean of group A
     * @param meanB        sample mean of group B
     * @param significant  true if pValue < alpha
     */
    public record Result(
        double tStatistic,
        double df,
        double pValue,
        double meanA,
        double meanB,
        boolean significant
    ) {}

    /**
     * Perform Welch's t-test on two samples.
     *
     * @param sampleA scores from prompt A
     * @param sampleB scores from prompt B
     * @param alpha   significance threshold (typically 0.05)
     * @return test result
     */
    public static @NonNull Result test(@NonNull List<Double> sampleA, @NonNull List<Double> sampleB, double alpha) {
        if (sampleA.size() < 2 || sampleB.size() < 2) {
            return new Result(0.0, 0.0, 1.0, mean(sampleA), mean(sampleB), false);
        }

        double meanA = mean(sampleA);
        double meanB = mean(sampleB);
        double varA = variance(sampleA, meanA);
        double varB = variance(sampleB, meanB);
        int nA = sampleA.size();
        int nB = sampleB.size();

        double se = Math.sqrt(varA / nA + varB / nB);
        if (se == 0.0) {
            return new Result(0.0, 0.0, 1.0, meanA, meanB, false);
        }

        double t = (meanA - meanB) / se;

        double num = Math.pow(varA / nA + varB / nB, 2);
        double den = Math.pow(varA / nA, 2) / (nA - 1) + Math.pow(varB / nB, 2) / (nB - 1);
        double df = den > 0 ? num / den : 1.0;

        double pValue = twoTailedPValue(Math.abs(t), df);
        boolean significant = pValue < alpha;

        return new Result(t, df, pValue, meanA, meanB, significant);
    }

    private static double mean(@NonNull List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static double variance(@NonNull List<Double> values, double mean) {
        return values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum() / (values.size() - 1);
    }

    /**
     * Approximate two-tailed p-value using the regularized incomplete beta function.
     * Uses a simple approximation based on the t-distribution CDF.
     */
    private static double twoTailedPValue(double t, double df) {
        // Use Student's t CDF approximation via the incomplete beta function
        // P = I_x(df/2, 1/2) where x = df / (df + t^2)
        double x = df / (df + t * t);
        double beta = incompleteBeta(x, df / 2.0, 0.5);
        return Math.min(1.0, Math.max(0.0, beta));
    }

    /**
     * Approximation of the incomplete beta function I_x(a,b) using continued fractions.
     * Sufficient for p-value estimation in A/B testing.
     */
    private static double incompleteBeta(double x, double a, double b) {
        if (x <= 0.0) return 0.0;
        if (x >= 1.0) return 1.0;

        double bt = Math.exp(
            logGamma(a + b) - logGamma(a) - logGamma(b)
                + a * Math.log(x) + b * Math.log(1.0 - x)
        );

        if (x < (a + 1.0) / (a + b + 2.0)) {
            return bt * continuedBetaFraction(x, a, b) / a;
        } else {
            return 1.0 - bt * continuedBetaFraction(1.0 - x, b, a) / b;
        }
    }

    private static double continuedBetaFraction(double x, double a, double b) {
        int maxIterations = 200;
        double epsilon = 3e-7;
        double am = 1.0;
        double bm = 1.0;
        double az = 1.0;
        double qab = a + b;
        double qap = a + 1.0;
        double qam = a - 1.0;
        double bz = 1.0 - qab * x / qap;

        for (int m = 1; m <= maxIterations; m++) {
            int m2 = 2 * m;
            double d = m * (b - m) * x / ((qam + m2) * (a + m2));
            double ap = az + d * am;
            double bp = bz + d * bm;
            d = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
            double app = ap + d * az;
            double bpp = bp + d * bz;
            double aold = az;
            am = ap / bpp;
            bm = bp / bpp;
            az = app / bpp;
            bz = 1.0;
            if (Math.abs(az - aold) < epsilon * Math.abs(az)) {
                return az;
            }
        }
        return az;
    }

    private static double logGamma(double x) {
        // Lanczos approximation
        double[] p = {
            676.5203681218851, -1259.1392167224028, 771.32342877765313,
            -176.61502916214059, 12.507343278686905, -0.13857109526572012,
            9.9843695780195716e-6, 1.5056327351493116e-7
        };
        double y = x;
        double a = 0.99999999999980993;
        for (int i = 0; i < p.length; i++) {
            a += p[i] / (++y);
        }
        double t = x + p.length - 0.5;
        return 0.5 * Math.log(2 * Math.PI) + Math.log(a) - t + (x - 0.5) * Math.log(t);
    }
}
