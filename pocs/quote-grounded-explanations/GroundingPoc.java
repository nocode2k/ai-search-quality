import java.text.Normalizer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class GroundingPoc {
    private static final int MIN_QUOTE_CHARS = 3;
    private static final int MAX_QUOTE_WORDS = 12;

    record Claim(String id, String explanation, String evidenceQuote, String evidenceText, boolean supported) {
    }

    record Metrics(double precision, double recall, double exposure, int accepted) {
        String csv(String mode) {
            return String.format(Locale.ROOT, "%s,%.4f,%.4f,%.4f,%d", mode, precision, recall, exposure, accepted);
        }
    }

    public static void main(String[] args) throws IOException {
        runAssertions();
        Path dataset = Path.of(args.length == 0 ? "data/claims.tsv" : args[0]);
        List<Claim> claims = claims(dataset);
        Metrics baseline = evaluate(claims, false);
        Metrics verified = evaluate(claims, true);

        System.out.println("GroundingPocTest: PASS");
        System.out.println("mode,precision,supported_recall,unsupported_exposure_rate,accepted");
        System.out.println(baseline.csv("baseline"));
        System.out.println(verified.csv("quote-verifier"));
        System.out.printf(Locale.ROOT, "validation_p95_us=%.3f%n", latencyP95Micros(claims, 10_000));
    }

    static boolean isGrounded(Claim claim) {
        if (claim == null) return false;
        String quote = canonicalize(claim.evidenceQuote());
        String source = canonicalize(claim.evidenceText());
        if (quote.length() < MIN_QUOTE_CHARS || quote.isBlank() || source.isBlank()) return false;
        if (quote.split(" ").length > MAX_QUOTE_WORDS) return false;
        return (" " + source + " ").contains(" " + quote + " ");
    }

    static String canonicalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    static Metrics evaluate(List<Claim> claims, boolean verify) {
        int accepted = 0;
        int supportedAccepted = 0;
        int unsupportedAccepted = 0;
        int supportedTotal = 0;
        int unsupportedTotal = 0;
        for (Claim claim : claims) {
            if (claim.supported()) supportedTotal++; else unsupportedTotal++;
            boolean display = !verify || isGrounded(claim);
            if (display) {
                accepted++;
                if (claim.supported()) supportedAccepted++; else unsupportedAccepted++;
            }
        }
        double precision = accepted == 0 ? 0.0 : (double) supportedAccepted / accepted;
        double recall = supportedTotal == 0 ? 0.0 : (double) supportedAccepted / supportedTotal;
        double exposure = unsupportedTotal == 0 ? 0.0 : (double) unsupportedAccepted / unsupportedTotal;
        return new Metrics(precision, recall, exposure, accepted);
    }

    static double latencyP95Micros(List<Claim> claims, int iterations) {
        for (int i = 0; i < 1_000; i++) isGrounded(claims.get(i % claims.size()));
        List<Long> samples = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            Claim claim = claims.get(i % claims.size());
            long start = System.nanoTime();
            isGrounded(claim);
            samples.add(System.nanoTime() - start);
        }
        Collections.sort(samples);
        int index = (int) Math.ceil(samples.size() * 0.95) - 1;
        return samples.get(Math.max(index, 0)) / 1_000.0;
    }

    static List<Claim> claims(Path path) throws IOException {
        List<Claim> result = new ArrayList<>();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\\t", -1);
            if (fields.length != 5) throw new IllegalArgumentException("Invalid dataset row: " + line);
            result.add(new Claim(fields[0], fields[1], fields[2], fields[3], Boolean.parseBoolean(fields[4])));
        }
        return List.copyOf(result);
    }

    static void runAssertions() {
        assertGrounded("JAVA 17 / Spring Boot", "Java 17 Spring Boot");
        assertGrounded("리뷰: '주문 다음 날 도착'해서 만족합니다.", "주문 다음 날 도착");
        assertRejected("기술: JavaScript, TypeScript", "Java");
        assertRejected("최대 24시간 사용할 수 있습니다.", "48시간 연속 사용");
        assertRejected("a 등급 상품", "a");
    }

    static void assertGrounded(String source, String quote) {
        if (!isGrounded(new Claim("T", "", quote, source, true))) {
            throw new AssertionError("Expected grounded quote: " + quote);
        }
    }

    static void assertRejected(String source, String quote) {
        if (isGrounded(new Claim("T", "", quote, source, false))) {
            throw new AssertionError("Expected rejected quote: " + quote);
        }
    }
}
