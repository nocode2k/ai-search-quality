import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ModularRagEvidenceExperiment {
    private static final int TOP_K = 3;

    record Candidate(
            String id,
            double vectorScore,
            double injection,
            double premiseConflict,
            double relevance,
            double evidence,
            double answerability,
            int gain) {}

    record Result(List<Candidate> documents, boolean fallback, int decisionCalls, long elapsedMicros) {}

    interface DecisionClient {
        Candidate classify(Candidate candidate);
    }

    static final class SyntheticDecisionClient implements DecisionClient {
        private final boolean timeout;

        SyntheticDecisionClient(boolean timeout) {
            this.timeout = timeout;
        }

        @Override
        public Candidate classify(Candidate candidate) {
            if (timeout) throw new DecisionUnavailableException("simulated timeout");
            return candidate;
        }
    }

    static final class DecisionUnavailableException extends RuntimeException {
        DecisionUnavailableException(String message) { super(message); }
    }

    static List<Candidate> baseline(List<Candidate> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::vectorScore).reversed())
                .limit(TOP_K)
                .toList();
    }

    static Result treatment(List<Candidate> candidates, DecisionClient client) {
        long start = System.nanoTime();
        List<Candidate> survivors = new ArrayList<>();
        int calls = 0;
        try {
            for (Candidate raw : candidates) {
                Candidate c = client.classify(raw);
                calls++;
                if (c.injection() > 0.70) continue;
                // A contradiction is deliberately retained so false premises can be corrected.
                if (c.relevance() < 0.45 || c.evidence() < 0.55) continue;
                survivors.add(c);
            }
            List<Candidate> ranked = survivors.stream()
                    .sorted(Comparator.comparingDouble(Candidate::answerability).reversed())
                    .limit(TOP_K)
                    .toList();
            return new Result(ranked, false, calls, elapsedMicros(start));
        } catch (DecisionUnavailableException unavailable) {
            return new Result(baseline(candidates), true, calls, elapsedMicros(start));
        }
    }

    static long elapsedMicros(long start) {
        return (System.nanoTime() - start) / 1_000;
    }

    static double precisionAtK(List<Candidate> ranked) {
        if (ranked.isEmpty()) return 0.0;
        long relevant = ranked.stream().filter(c -> c.gain() > 0).count();
        return (double) relevant / ranked.size();
    }

    static double ndcgAtK(List<Candidate> ranked, List<Candidate> all) {
        double dcg = dcg(ranked);
        double ideal = dcg(all.stream()
                .sorted(Comparator.comparingInt(Candidate::gain).reversed())
                .limit(TOP_K)
                .toList());
        return ideal == 0.0 ? 0.0 : dcg / ideal;
    }

    static double dcg(List<Candidate> ranked) {
        double score = 0.0;
        for (int i = 0; i < ranked.size(); i++) {
            score += (Math.pow(2, ranked.get(i).gain()) - 1) / (Math.log(i + 2) / Math.log(2));
        }
        return score;
    }

    static long injectionLeakage(List<Candidate> ranked) {
        return ranked.stream().filter(c -> c.injection() > 0.70).count();
    }

    static List<String> ids(List<Candidate> ranked) {
        return ranked.stream().map(Candidate::id).toList();
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        List<Candidate> corpus = List.of(
                new Candidate("topic-only-reference", 0.95, 0.02, 0.05, 0.90, 0.20, 0.18, 0),
                new Candidate("prompt-injection", 0.91, 0.96, 0.02, 0.86, 0.60, 0.55, 0),
                new Candidate("partial-evidence", 0.87, 0.01, 0.03, 0.91, 0.72, 0.74, 2),
                new Candidate("direct-answer", 0.82, 0.01, 0.02, 0.96, 0.95, 0.97, 3),
                new Candidate("corrects-false-premise", 0.77, 0.01, 0.91, 0.94, 0.90, 0.92, 3),
                new Candidate("related-no-answer", 0.72, 0.02, 0.01, 0.67, 0.31, 0.25, 0));

        long baselineStart = System.nanoTime();
        List<Candidate> baseline = baseline(corpus);
        long baselineMicros = elapsedMicros(baselineStart);
        Result treatment = treatment(corpus, new SyntheticDecisionClient(false));
        Result fallback = treatment(corpus, new SyntheticDecisionClient(true));

        double baselinePrecision = precisionAtK(baseline);
        double treatmentPrecision = precisionAtK(treatment.documents());
        double baselineNdcg = ndcgAtK(baseline, corpus);
        double treatmentNdcg = ndcgAtK(treatment.documents(), corpus);

        require(treatmentPrecision > baselinePrecision, "treatment must improve precision@3");
        require(treatmentNdcg > baselineNdcg, "treatment must improve nDCG@3");
        require(injectionLeakage(treatment.documents()) == 0, "injection chunk must be removed");
        require(treatment.documents().stream().anyMatch(c -> c.premiseConflict() > 0.70),
                "premise-conflicting evidence must be retained");
        require(fallback.fallback(), "timeout must activate fallback");
        require(ids(fallback.documents()).equals(ids(baseline)), "fallback must preserve baseline results");

        System.out.printf("baseline.ids=%s%n", ids(baseline));
        System.out.printf("treatment.ids=%s%n", ids(treatment.documents()));
        System.out.printf("baseline.precision@3=%.4f%n", baselinePrecision);
        System.out.printf("treatment.precision@3=%.4f%n", treatmentPrecision);
        System.out.printf("baseline.ndcg@3=%.4f%n", baselineNdcg);
        System.out.printf("treatment.ndcg@3=%.4f%n", treatmentNdcg);
        System.out.printf("treatment.prompt_injection_leakage=%d%n", injectionLeakage(treatment.documents()));
        System.out.printf("treatment.decision_calls=%d%n", treatment.decisionCalls());
        System.out.printf("baseline.elapsed_us=%d%n", baselineMicros);
        System.out.printf("treatment.elapsed_us=%d%n", treatment.elapsedMicros());
        System.out.printf("fallback.activated=%s%n", fallback.fallback());
        System.out.println("verification=PASS");
    }
}
