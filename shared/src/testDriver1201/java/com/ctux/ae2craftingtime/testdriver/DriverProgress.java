package com.ctux.ae2craftingtime.testdriver;

import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;

final class DriverProgress {
    static final int MOVE_ATTEMPTS = 25;
    private final Path output;
    private final ProgressMover mover;
    private String checkpoint;
    private Instant checkpointAt = Instant.now();
    private Instant callbackAt = checkpointAt;
    private long callbackSequence;
    private long checkpointSequence;
    private long lastWrite;

    DriverProgress(Path output, String checkpoint) throws IOException {
        this(output, checkpoint, (source, target) -> Files.move(source, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    DriverProgress(Path output, String checkpoint, ProgressMover mover) throws IOException {
        this.output = output;
        this.mover = mover;
        this.checkpoint = checkpoint;
        write();
    }

    synchronized void callback(String value) {
        callbackSequence++;
        callbackAt = Instant.now();
        boolean changed = !value.equals(checkpoint);
        if (changed) {
            checkpoint = value;
            checkpointAt = callbackAt;
            checkpointSequence++;
        }
        if (changed || System.nanoTime() - lastWrite >= java.time.Duration.ofSeconds(1).toNanos()) {
            try {
                write();
            } catch (IOException error) {
                throw new IllegalStateException("Cannot write UI-smoke progress", error);
            }
        }
    }

    private void write() throws IOException {
        Files.createDirectories(output);
        var value = new LinkedHashMap<String, Object>();
        value.put("schema", 1);
        value.put("pid", ProcessHandle.current().pid());
        value.put("callbackAt", callbackAt.toString());
        value.put("checkpointAt", checkpointAt.toString());
        value.put("callbackSequence", callbackSequence);
        value.put("checkpointSequence", checkpointSequence);
        value.put("checkpoint", checkpoint);
        var temporary = output.resolve("driver-progress.json.tmp");
        Files.writeString(temporary, new GsonBuilder().create().toJson(value), StandardCharsets.UTF_8);
        moveWithAccessDeniedRetry(temporary, output.resolve("driver-progress.json"), mover);
        lastWrite = System.nanoTime();
    }

    static void moveWithAccessDeniedRetry(Path source, Path target, ProgressMover mover) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                mover.move(source, target);
                return;
            } catch (AccessDeniedException error) {
                if (attempt == MOVE_ATTEMPTS) throw error;
                try {
                    Thread.sleep(10);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while replacing UI-smoke file", interrupted);
                }
            }
        }
    }

    @FunctionalInterface
    interface ProgressMover {
        void move(Path source, Path target) throws IOException;
    }
}
