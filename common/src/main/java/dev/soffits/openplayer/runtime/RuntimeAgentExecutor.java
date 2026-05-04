package dev.soffits.openplayer.runtime;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;

public final class RuntimeAgentExecutor {
    private static final int MAX_THREADS = 4;
    private static final int MAX_QUEUED_WORK = 32;
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(workerCount(), workerCount(), 0L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(MAX_QUEUED_WORK), new ThreadFactory() {
        private final AtomicInteger nextId = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "OpenPlayer agent worker " + nextId.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    });

    private RuntimeAgentExecutor() {
    }

    public static <T> void submit(MinecraftServer server, Supplier<T> work, Consumer<T> completion,
                                  Consumer<RuntimeException> failure) {
        if (server == null) {
            throw new IllegalArgumentException("server cannot be null");
        }
        if (work == null) {
            throw new IllegalArgumentException("work cannot be null");
        }
        if (completion == null) {
            throw new IllegalArgumentException("completion cannot be null");
        }
        if (failure == null) {
            throw new IllegalArgumentException("failure cannot be null");
        }
        try {
            EXECUTOR.execute(() -> {
                try {
                    T result = work.get();
                    server.execute(() -> completion.accept(result));
                } catch (RuntimeException exception) {
                    server.execute(() -> failure.accept(exception));
                }
            });
        } catch (RejectedExecutionException exception) {
            server.execute(() -> failure.accept(exception));
        }
    }

    private static int workerCount() {
        int processors = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(MAX_THREADS, Math.max(2, processors / 2)));
    }
}
