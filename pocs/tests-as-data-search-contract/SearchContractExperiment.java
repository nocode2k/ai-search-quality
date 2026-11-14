import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SearchContractExperiment {
    private record Case(String name, String query, String expected) {}

    private enum Mutation {
        DROP_NOT,
        SWAP_AND_OR,
        SPLIT_PHRASE,
        DROP_WILDCARD
    }

    private static final List<Case> BASELINE_CASES = List.of(
            new Case("single term", "battery", "TERM(battery)"),
            new Case("and query", "battery AND charger", "TERM(battery) AND TERM(charger)"),
            new Case("phrase query", "\"electric vehicle\"", "PHRASE(electric vehicle)")
    );

    public static void main(String[] args) throws Exception {
        Path contractPath = args.length == 0 ? Path.of("contracts/search-contract.tsv") : Path.of(args[0]);
        List<Case> contractCases = loadContract(contractPath);

        Result baseline = evaluate("tests-as-code baseline", BASELINE_CASES);
        Result experiment = evaluate("tests-as-data contract", contractCases);

        System.out.printf(Locale.ROOT,
                "%s: cases=%d, killed=%d/%d, mutation-kill-rate=%.3f, p95-us=%.3f%n",
                baseline.label, baseline.caseCount, baseline.killed, Mutation.values().length,
                baseline.killRate(), baseline.p95Nanos / 1_000.0);
        System.out.printf(Locale.ROOT,
                "%s: cases=%d, killed=%d/%d, mutation-kill-rate=%.3f, p95-us=%.3f%n",
                experiment.label, experiment.caseCount, experiment.killed, Mutation.values().length,
                experiment.killRate(), experiment.p95Nanos / 1_000.0);
        System.out.println("contract-sha256=" + sha256(contractPath));

        if (experiment.killed != Mutation.values().length) {
            throw new IllegalStateException("The data contract did not detect every seeded functional fault");
        }
    }

    private static Result evaluate(String label, List<Case> cases) {
        int killed = 0;
        for (Mutation mutation : Mutation.values()) {
            if (cases.stream().anyMatch(testCase -> !normalize(testCase.query, mutation).equals(testCase.expected))) {
                killed++;
            }
        }

        long[] durations = new long[2_000];
        for (int i = 0; i < durations.length; i++) {
            Case testCase = cases.get(i % cases.size());
            long start = System.nanoTime();
            String actual = normalize(testCase.query, null);
            durations[i] = System.nanoTime() - start;
            if (!actual.equals(testCase.expected)) {
                throw new AssertionError(testCase.name + ": expected=" + testCase.expected + ", actual=" + actual);
            }
        }
        Arrays.sort(durations);
        return new Result(label, cases.size(), killed, durations[(int) Math.ceil(durations.length * 0.95) - 1]);
    }

    private static String normalize(String query, Mutation mutation) {
        List<String> tokens = tokenize(query);
        List<String> rendered = new ArrayList<>();
        for (String token : tokens) {
            if (token.equals("AND") || token.equals("OR") || token.equals("NOT") || token.equals("(") || token.equals(")")) {
                if (mutation == Mutation.DROP_NOT && token.equals("NOT")) {
                    continue;
                }
                if (mutation == Mutation.SWAP_AND_OR && token.equals("AND")) {
                    rendered.add("OR");
                } else if (mutation == Mutation.SWAP_AND_OR && token.equals("OR")) {
                    rendered.add("AND");
                } else {
                    rendered.add(token);
                }
            } else if (token.startsWith("\"") && token.endsWith("\"")) {
                String value = token.substring(1, token.length() - 1);
                rendered.add(mutation == Mutation.SPLIT_PHRASE
                        ? "TERM(" + value.replace(" ", ") TERM(") + ")"
                        : "PHRASE(" + value + ")");
            } else {
                String value = mutation == Mutation.DROP_WILDCARD ? token.replace("*", "") : token;
                rendered.add("TERM(" + value.toLowerCase(Locale.ROOT) + ")");
            }
        }
        return String.join(" ", rendered);
    }

    private static List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < query.length(); i++) {
            char ch = query.charAt(i);
            if (ch == '"') {
                current.append(ch);
                quoted = !quoted;
            } else if (!quoted && (Character.isWhitespace(ch) || ch == '(' || ch == ')')) {
                flush(current, tokens);
                if (ch == '(' || ch == ')') tokens.add(String.valueOf(ch));
            } else {
                current.append(ch);
            }
        }
        if (quoted) throw new IllegalArgumentException("Unclosed phrase: " + query);
        flush(current, tokens);
        return tokens;
    }

    private static void flush(StringBuilder current, List<String> tokens) {
        if (current.length() > 0) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }

    private static List<Case> loadContract(Path path) throws IOException {
        List<Case> cases = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] columns = line.split("\\t", -1);
            if (columns.length != 3) throw new IllegalArgumentException("Expected 3 TSV columns: " + line);
            cases.add(new Case(columns[0], columns[1], columns[2]));
        }
        if (cases.isEmpty()) throw new IllegalArgumentException("Contract is empty: " + path);
        return cases;
    }

    private static String sha256(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        StringBuilder result = new StringBuilder();
        for (byte value : digest) result.append(String.format("%02x", value));
        return result.toString();
    }

    private record Result(String label, int caseCount, int killed, long p95Nanos) {
        double killRate() { return (double) killed / Mutation.values().length; }
    }
}
