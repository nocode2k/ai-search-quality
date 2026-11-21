import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GovernedPlanExperiment {
    private static final Set<String> ALLOWED = Set.of(
            "lexical_search", "vector_search", "catalog_filter",
            "rerank", "generate_explanation", "package_results");
    private static final Map<String, Integer> MAX_TIMEOUT_MS = Map.of(
            "lexical_search", 100,
            "vector_search", 150,
            "catalog_filter", 50,
            "rerank", 200,
            "generate_explanation", 2_000,
            "package_results", 50);
    private static final String FALLBACK =
            "s1=lexical_search(80)->;s2=catalog_filter(40)->s1;s3=package_results(30)->s2";

    private record Scenario(String name, boolean expectedValid, String plan) {}
    private record Step(String id, String capability, int timeoutMs, List<String> dependencies) {}
    private record Metrics(int tp, int fp, int tn, int fn, int fallbackCount, double p95Micros) {
        double precision() { return tp + fp == 0 ? 1.0 : (double) tp / (tp + fp); }
        double recall() { return tp + fn == 0 ? 1.0 : (double) tp / (tp + fn); }
        double unsafeAcceptanceRate() { return fp + tn == 0 ? 0.0 : (double) fp / (fp + tn); }
    }

    public static void main(String[] args) throws Exception {
        Path path = args.length == 0 ? Path.of("scenarios.tsv") : Path.of(args[0]);
        List<Scenario> scenarios = load(path);

        Metrics baseline = evaluate(scenarios, false);
        Metrics governed = evaluate(scenarios, true);

        print("unconstrained baseline", baseline, scenarios.size());
        print("governed plan validator", governed, scenarios.size());

        if (governed.fp != 0 || governed.fn != 0) {
            throw new IllegalStateException("Governed validator misclassified a synthetic plan");
        }
        if (!validate(parse(FALLBACK))) {
            throw new IllegalStateException("Fallback plan must always pass validation");
        }
    }

    private static Metrics evaluate(List<Scenario> scenarios, boolean governed) {
        int tp = 0, fp = 0, tn = 0, fn = 0, fallbacks = 0;
        for (Scenario scenario : scenarios) {
            boolean accepted = governed ? validate(parse(scenario.plan)) : !scenario.plan.isBlank();
            if (accepted && scenario.expectedValid) tp++;
            else if (accepted) fp++;
            else if (scenario.expectedValid) fn++;
            else {
                tn++;
                if (governed) {
                    fallbacks++;
                    if (!validate(parse(FALLBACK))) throw new AssertionError("unsafe fallback");
                }
            }
        }

        long[] nanos = new long[5_000];
        for (int i = 0; i < nanos.length; i++) {
            Scenario scenario = scenarios.get(i % scenarios.size());
            long start = System.nanoTime();
            if (governed) validate(parse(scenario.plan));
            else scenario.plan.isBlank();
            nanos[i] = System.nanoTime() - start;
        }
        java.util.Arrays.sort(nanos);
        double p95 = nanos[(int) Math.ceil(nanos.length * 0.95) - 1] / 1_000.0;
        return new Metrics(tp, fp, tn, fn, fallbacks, p95);
    }

    private static boolean validate(List<Step> steps) {
        if (steps.isEmpty() || steps.size() > 8) return false;
        Map<String, Step> byId = new HashMap<>();
        for (Step step : steps) {
            if (!ALLOWED.contains(step.capability)) return false;
            if (step.timeoutMs <= 0 || step.timeoutMs > MAX_TIMEOUT_MS.get(step.capability)) return false;
            if (byId.put(step.id, step) != null) return false;
        }
        for (Step step : steps) {
            if (!byId.keySet().containsAll(step.dependencies)) return false;
        }
        if (hasCycle(byId)) return false;

        List<Step> terminals = steps.stream()
                .filter(candidate -> steps.stream().noneMatch(other -> other.dependencies.contains(candidate.id)))
                .toList();
        if (terminals.size() != 1 || !terminals.getFirst().capability.equals("package_results")) return false;

        Step packageStep = terminals.getFirst();
        Set<String> ancestors = new HashSet<>();
        collectAncestors(packageStep.id, byId, ancestors);
        if (ancestors.size() != steps.size()) return false;

        boolean hasRetrieval = steps.stream().anyMatch(s ->
                s.capability.equals("lexical_search") || s.capability.equals("vector_search"));
        boolean hasFilter = steps.stream().anyMatch(s -> s.capability.equals("catalog_filter"));
        return hasRetrieval && hasFilter;
    }

    private static boolean hasCycle(Map<String, Step> steps) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String id : steps.keySet()) if (visit(id, steps, visiting, visited)) return true;
        return false;
    }

    private static boolean visit(String id, Map<String, Step> steps, Set<String> visiting, Set<String> visited) {
        if (visiting.contains(id)) return true;
        if (!visited.add(id)) return false;
        visiting.add(id);
        for (String dependency : steps.get(id).dependencies) {
            if (visit(dependency, steps, visiting, visited)) return true;
        }
        visiting.remove(id);
        return false;
    }

    private static void collectAncestors(String id, Map<String, Step> steps, Set<String> seen) {
        if (!seen.add(id)) return;
        for (String dependency : steps.get(id).dependencies) collectAncestors(dependency, steps, seen);
    }

    private static List<Step> parse(String plan) {
        List<Step> steps = new ArrayList<>();
        for (String raw : plan.split(";")) {
            String[] sides = raw.split("->", -1);
            String[] assignment = sides[0].split("=", 2);
            int open = assignment[1].lastIndexOf('(');
            int close = assignment[1].lastIndexOf(')');
            String capability = assignment[1].substring(0, open);
            int timeout = Integer.parseInt(assignment[1].substring(open + 1, close));
            List<String> dependencies = sides.length < 2 || sides[1].isBlank()
                    ? List.of() : List.of(sides[1].split("\\+"));
            steps.add(new Step(assignment[0], capability, timeout, dependencies));
        }
        return steps;
    }

    private static List<Scenario> load(Path path) throws IOException {
        List<Scenario> scenarios = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] columns = line.split("\\t", 3);
            scenarios.add(new Scenario(columns[0], Boolean.parseBoolean(columns[1]), columns[2]));
        }
        return scenarios;
    }

    private static void print(String label, Metrics metrics, int count) {
        System.out.printf(Locale.ROOT,
                "%s: scenarios=%d, precision=%.3f, recall=%.3f, unsafe-acceptance=%.3f, fallbacks=%d, p95-us=%.3f%n",
                label, count, metrics.precision(), metrics.recall(),
                metrics.unsafeAcceptanceRate(), metrics.fallbackCount, metrics.p95Micros);
    }
}
