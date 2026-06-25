package qdrantservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

@Component
@Slf4j
public class BatchSearchRunner {

    private final ExecutorService executor =
            Executors.newVirtualThreadPerTaskExecutor();

    private final Semaphore permits;
    private final long permitWaitMs;

    public BatchSearchRunner(
            @Value("${rag.batch-search.max-concurrent-queries:32}") int maxConcurrency,
            @Value("${rag.batch-search.permit-wait-ms:50000}") long permitWaitMs
    ) {
        this.permits = new Semaphore(maxConcurrency, true);
        this.permitWaitMs = permitWaitMs;
    }

    public <T> List<T> execute(
            List<Callable<T>> tasks,
            Duration timeout
    ) {
        List<Callable<T>> guardedTasks = tasks.stream()
                .map(task -> (Callable<T>) () -> {
                    boolean acquired = permits.tryAcquire(
                            permitWaitMs,
                            TimeUnit.MILLISECONDS
                    );

                    if (!acquired) {
                        throw new RejectedExecutionException(
                                "Hybrid search concurrency limit reached"
                        );
                    }

                    try {
                        return task.call();
                    } finally {
                        permits.release();
                    }
                })
                .toList();

        try {
            List<Future<T>> futures = executor.invokeAll(
                    guardedTasks,
                    timeout.toMillis(),
                    TimeUnit.MILLISECONDS
            );

            return futures.stream()
                    .filter(future -> !future.isCancelled())
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            log.warn("Batch search task failed: {}", e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Batch search interrupted", e);
        }
    }

    @PreDestroy
    public void close() {
        executor.close();
    }
}
