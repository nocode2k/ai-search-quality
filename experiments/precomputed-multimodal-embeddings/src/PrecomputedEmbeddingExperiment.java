import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PrecomputedEmbeddingExperiment {
    private static final int TOP_K = 3;
    private static final String MODEL_VERSION = "synthetic-multimodal-v1";

    record Product(String sku, String title, String description, boolean inStock,
                   float[] suppliedVector, String gainGroup) {}
    record Query(String id, String text, float[] suppliedVector, String relevantGroup) {}
    record EmbeddedRow(Product product, String modelVersion) {}
    record Ranked(Product product, double score, int gain) {}
    record Metrics(double recallAt3, double ndcgAt3, double zeroResultRate, long elapsedMicros) {}

    static final class InMemorySuppliedVectorStore {
        private final int dimensions;
        private final Map<String, EmbeddedRow> rows = new HashMap<>();

        InMemorySuppliedVectorStore(int dimensions) { this.dimensions = dimensions; }

        void upsert(List<EmbeddedRow> batch) {
            for (EmbeddedRow row : batch) validate(row.product().suppliedVector());
            for (EmbeddedRow row : batch) rows.put(row.product().sku(), row);
        }

        List<Ranked> search(float[] queryVector, String modelVersion, String relevantGroup) {
            validate(queryVector);
            if (!MODEL_VERSION.equals(modelVersion)) {
                throw new IllegalArgumentException("incompatible embedding model version");
            }
            return rows.values().stream()
                    .filter(row -> row.product().inStock())
                    .filter(row -> modelVersion.equals(row.modelVersion()))
                    .map(row -> new Ranked(row.product(), cosine(queryVector, row.product().suppliedVector()),
                            row.product().gainGroup().equals(relevantGroup) ? 1 : 0))
                    .sorted(Comparator.comparingDouble(Ranked::score).reversed())
                    .limit(TOP_K)
                    .toList();
        }

        int size() { return rows.size(); }

        private void validate(float[] vector) {
            if (vector.length != dimensions) throw new IllegalArgumentException("dimension mismatch");
            for (float value : vector) {
                if (!Float.isFinite(value)) throw new IllegalArgumentException("non-finite vector");
            }
        }
    }

    static List<Ranked> lexical(Product[] products, Query query) {
        Set<String> queryTokens = tokens(query.text());
        return Arrays.stream(products)
                .filter(Product::inStock)
                .map(product -> new Ranked(product,
                        overlap(queryTokens, tokens(product.title() + " " + product.description())),
                        product.gainGroup().equals(query.relevantGroup()) ? 1 : 0))
                .filter(ranked -> ranked.score() > 0.0)
                .sorted(Comparator.comparingDouble(Ranked::score).reversed()
                        .thenComparing(ranked -> ranked.product().sku()))
                .limit(TOP_K)
                .toList();
    }

    static Metrics evaluate(Product[] products, List<Query> queries,
                            InMemorySuppliedVectorStore store, boolean treatment) {
        long start = System.nanoTime();
        double recall = 0.0;
        double ndcg = 0.0;
        int zero = 0;
        for (Query query : queries) {
            List<Ranked> ranked = treatment
                    ? store.search(query.suppliedVector(), MODEL_VERSION, query.relevantGroup())
                    : lexical(products, query);
            if (ranked.isEmpty()) zero++;
            long relevantTotal = Arrays.stream(products)
                    .filter(Product::inStock)
                    .filter(p -> p.gainGroup().equals(query.relevantGroup()))
                    .count();
            long found = ranked.stream().filter(r -> r.gain() > 0).count();
            recall += relevantTotal == 0 ? 1.0 : (double) found / relevantTotal;
            ndcg += ndcg(ranked, Math.min(TOP_K, (int) relevantTotal));
        }
        return new Metrics(recall / queries.size(), ndcg / queries.size(),
                (double) zero / queries.size(), elapsedMicros(start));
    }

    static double ndcg(List<Ranked> ranked, int idealRelevant) {
        double dcg = 0.0;
        for (int i = 0; i < ranked.size(); i++) {
            dcg += ranked.get(i).gain() / log2(i + 2);
        }
        double ideal = 0.0;
        for (int i = 0; i < idealRelevant; i++) ideal += 1.0 / log2(i + 2);
        return ideal == 0.0 ? 1.0 : dcg / ideal;
    }

    static double cosine(float[] left, float[] right) {
        double dot = 0.0, leftNorm = 0.0, rightNorm = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) return 0.0;
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    static Set<String> tokens(String text) {
        Set<String> result = new HashSet<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!token.isBlank()) result.add(token);
        }
        return result;
    }

    static double overlap(Set<String> left, Set<String> right) {
        long matches = left.stream().filter(right::contains).count();
        return left.isEmpty() ? 0.0 : (double) matches / left.size();
    }

    static Product[] loadProducts(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        List<Product> products = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] c = line.split(",", -1);
            products.add(new Product(c[0], c[1], c[2], Boolean.parseBoolean(c[3]),
                    new float[]{Float.parseFloat(c[4]), Float.parseFloat(c[5]), Float.parseFloat(c[6])}, c[7]));
        }
        return products.toArray(Product[]::new);
    }

    static List<Query> loadQueries(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        List<Query> queries = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] c = line.split(",", -1);
            queries.add(new Query(c[0], c[1],
                    new float[]{Float.parseFloat(c[2]), Float.parseFloat(c[3]), Float.parseFloat(c[4])}, c[5]));
        }
        return queries;
    }

    static boolean rejects(Runnable action) {
        try { action.run(); return false; }
        catch (IllegalArgumentException expected) { return true; }
    }

    static long elapsedMicros(long start) { return (System.nanoTime() - start) / 1_000; }
    static double log2(double value) { return Math.log(value) / Math.log(2.0); }
    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        Product[] products = loadProducts(Path.of(args[0]));
        List<Query> queries = loadQueries(Path.of(args[1]));
        InMemorySuppliedVectorStore store = new InMemorySuppliedVectorStore(3);
        List<EmbeddedRow> batch = Arrays.stream(products).map(p -> new EmbeddedRow(p, MODEL_VERSION)).toList();
        store.upsert(batch);
        int sizeAfterFirst = store.size();
        store.upsert(batch);
        int duplicatesAfterRetry = store.size() - sizeAfterFirst;

        Metrics baseline = evaluate(products, queries, store, false);
        Metrics treatment = evaluate(products, queries, store, true);
        boolean dimensionGuard = rejects(() -> store.search(new float[]{1f, 2f}, MODEL_VERSION, "none"));
        boolean finiteGuard = rejects(() -> store.search(new float[]{1f, Float.NaN, 2f}, MODEL_VERSION, "none"));
        boolean versionGuard = rejects(() -> store.search(new float[]{1f, 0f, 0f}, "other-model", "none"));

        require(treatment.recallAt3() > baseline.recallAt3(), "treatment must improve recall@3");
        require(treatment.ndcgAt3() > baseline.ndcgAt3(), "treatment must improve nDCG@3");
        require(treatment.zeroResultRate() <= baseline.zeroResultRate(), "treatment must not increase zero results");
        require(duplicatesAfterRetry == 0, "stable-ID retry must be idempotent");
        require(dimensionGuard && finiteGuard && versionGuard, "all vector compatibility guards must pass");

        System.out.printf("baseline.recall@3=%.4f%n", baseline.recallAt3());
        System.out.printf("treatment.recall@3=%.4f%n", treatment.recallAt3());
        System.out.printf("baseline.ndcg@3=%.4f%n", baseline.ndcgAt3());
        System.out.printf("treatment.ndcg@3=%.4f%n", treatment.ndcgAt3());
        System.out.printf("baseline.zero_result_rate=%.4f%n", baseline.zeroResultRate());
        System.out.printf("treatment.zero_result_rate=%.4f%n", treatment.zeroResultRate());
        System.out.printf("baseline.elapsed_us=%d%n", baseline.elapsedMicros());
        System.out.printf("treatment.elapsed_us=%d%n", treatment.elapsedMicros());
        System.out.printf("duplicate_count_after_retry=%d%n", duplicatesAfterRetry);
        System.out.printf("dimension_guard=%s%n", dimensionGuard ? "PASS" : "FAIL");
        System.out.printf("finite_guard=%s%n", finiteGuard ? "PASS" : "FAIL");
        System.out.printf("model_version_guard=%s%n", versionGuard ? "PASS" : "FAIL");
        System.out.println("verification=PASS");
    }
}
