import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class AgentToolIdempotencyExperiment {
    record Operation(String operationId, String stepId, String tool, String argument, int retries) {
        String idempotencyKey() {
            return operationId + ":" + stepId + ":" + tool + ":" + Integer.toHexString(argument.hashCode());
        }
    }
    record Result(String receipt) {}
    record Measurement(String name, int logicalOperations, int attempts, int sideEffects,
                       double duplicateRate, double consistentResultRate, double p95Millis, double p99Millis) {}
    interface ToolInvoker {
        Result invoke(Operation operation);
        int sideEffects();
    }
    static final class NaiveInvoker implements ToolInvoker {
        private final AtomicInteger effects = new AtomicInteger();
        public Result invoke(Operation operation) {
            simulateWork(operation);
            return new Result("receipt-" + effects.incrementAndGet());
        }
        public int sideEffects() { return effects.get(); }
    }
    static final class IdempotentInvoker implements ToolInvoker {
        private final AtomicInteger effects = new AtomicInteger();
        private final Map<String, CompletableFuture<Result>> claims = new ConcurrentHashMap<>();
        public Result invoke(Operation operation) {
            CompletableFuture<Result> candidate = new CompletableFuture<>();
            CompletableFuture<Result> winner = claims.putIfAbsent(operation.idempotencyKey(), candidate);
            if (winner != null) return winner.join();
            try {
                simulateWork(operation);
                Result result = new Result("receipt-" + effects.incrementAndGet());
                candidate.complete(result);
                return result;
            } catch (Throwable error) {
                candidate.completeExceptionally(error);
                claims.remove(operation.idempotencyKey(), candidate);
                throw error;
            }
        }
        public int sideEffects() { return effects.get(); }
    }
    public static void main(String[] args) throws Exception {
        Path input = args.length == 0 ? Path.of("data/logical-operations.csv") : Path.of(args[0]);
        List<Operation> operations = readOperations(input);
        Measurement baseline = run("naive", operations, new NaiveInvoker());
        Measurement experiment = run("idempotent", operations, new IdempotentInvoker());
        System.out.println(toJson(baseline, experiment));
        verify(baseline, experiment);
    }
    static Measurement run(String name, List<Operation> operations, ToolInvoker invoker) {
        int attempts = operations.stream().mapToInt(Operation::retries).sum();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(attempts, 32));
        CountDownLatch start = new CountDownLatch(1);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        Map<String, List<String>> receipts = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (Operation operation : operations) {
            for (int attempt = 0; attempt < operation.retries(); attempt++) {
                tasks.add(CompletableFuture.runAsync(() -> {
                    try {
                        start.await();
                        long began = System.nanoTime();
                        Result result = invoker.invoke(operation);
                        latencies.add(System.nanoTime() - began);
                        receipts.computeIfAbsent(operation.operationId(),
                                ignored -> Collections.synchronizedList(new ArrayList<>())).add(result.receipt());
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(interrupted);
                    }
                }, pool));
            }
        }
        start.countDown();
        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
        pool.shutdown();
        long consistent = receipts.values().stream().filter(v -> v.stream().distinct().count() == 1).count();
        int effects = invoker.sideEffects();
        return new Measurement(name, operations.size(), attempts, effects,
                (double) Math.max(0, effects - operations.size()) / operations.size(),
                (double) consistent / operations.size(), percentileMillis(latencies, 0.95),
                percentileMillis(latencies, 0.99));
    }
    static List<Operation> readOperations(Path path) throws IOException {
        return Files.readAllLines(path).stream().skip(1).filter(line -> !line.isBlank()).map(line -> {
            String[] f = line.split(",", -1);
            if (f.length != 5) throw new IllegalArgumentException("invalid CSV row: " + line);
            return new Operation(f[0], f[1], f[2], f[3], Integer.parseInt(f[4]));
        }).toList();
    }
    static void simulateWork(Operation operation) {
        try { Thread.sleep(2L + Math.floorMod(operation.argument().hashCode(), 4)); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(interrupted);
        }
    }
    static double percentileMillis(List<Long> values, double percentile) {
        List<Long> sorted = values.stream().sorted().toList();
        int index = Math.min(sorted.size() - 1, (int) Math.ceil(percentile * sorted.size()) - 1);
        return Duration.ofNanos(sorted.get(index)).toNanos() / 1_000_000.0;
    }
    static void verify(Measurement baseline, Measurement experiment) {
        if (baseline.sideEffects() <= baseline.logicalOperations())
            throw new AssertionError("baseline did not reproduce duplicate side effects");
        if (experiment.sideEffects() != experiment.logicalOperations())
            throw new AssertionError("idempotent strategy executed duplicate side effects");
        if (experiment.consistentResultRate() != 1.0)
            throw new AssertionError("retries did not receive the cached winner result");
    }
    static String toJson(Measurement baseline, Measurement experiment) {
        return """
                {
                  "baseline": %s,
                  "experiment": %s,
                  "notes": "Latency is measured locally per retry; it is not a production SLA."
                }
                """.formatted(measurementJson(baseline), measurementJson(experiment));
    }
    static String measurementJson(Measurement m) {
        return """
                {
                    "strategy": "%s",
                    "logicalOperations": %d,
                    "attempts": %d,
                    "sideEffects": %d,
                    "duplicateSideEffectRate": %.4f,
                    "consistentResultRate": %.4f,
                    "p95Millis": %.3f,
                    "p99Millis": %.3f
                  }""".formatted(m.name(), m.logicalOperations(), m.attempts(), m.sideEffects(),
                m.duplicateRate(), m.consistentResultRate(), m.p95Millis(), m.p99Millis());
    }
}
