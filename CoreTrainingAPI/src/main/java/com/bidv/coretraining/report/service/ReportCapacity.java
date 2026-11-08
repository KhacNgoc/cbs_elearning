package com.bidv.coretraining.report.service;

import com.bidv.coretraining.exception.ReportBusyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Caps how many whole-population report statements run at once in this instance. Each one sorts and hashes
 * every learner-course pair, so at large data a handful in parallel fill the database's memory and temp disk
 * and slow every other screen. Requests beyond the cap wait briefly, then get 503 with Retry-After.
 * Callers take a slot before opening a transaction, so a waiting request holds no database connection.
 */
@Component
public class ReportCapacity {
    @FunctionalInterface
    public interface Work<T, E extends Exception> {
        T run() throws E;
    }

    private final Semaphore slots;
    private final long waitMillis;

    public ReportCapacity(@Value("${app.report.max-concurrent:2}") int maxConcurrent,
            @Value("${app.report.queue-wait-seconds:20}") long waitSeconds) {
        this.slots = new Semaphore(Math.max(1, maxConcurrent), true);
        this.waitMillis = Math.max(0, waitSeconds) * 1000;
    }

    public <T, E extends Exception> T run(Work<T, E> work) throws E {
        boolean acquired;
        try {
            acquired = slots.tryAcquire(waitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ReportBusyException();
        }
        if (!acquired)
            throw new ReportBusyException();
        try {
            return work.run();
        } finally {
            slots.release();
        }
    }
}
