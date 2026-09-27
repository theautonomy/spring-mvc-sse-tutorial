package com.example.sse.step8;

import com.example.sse.step8.JobRunner.FailureMode;
import com.example.sse.step8.JobStore.Status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A 1 s timeout with a slow database, and all 6 batches running at once: the first chunk of every batch takes about
 * 3 s, so the deadline passes while every batch's transaction is open. The stop must still be recorded, although
 * all those transactions roll back.
 */
@SpringBootTest(properties = {"tutorial.step8.job-timeout=1s", "tutorial.step8.max-parallel-batches=6"})
class JobTimeoutTests {

    @Autowired
    JobRunner runner;

    @Autowired
    JobStore store;

    @Test
    void jobThatRunsOutOfTimeStopsAndDeletesItsRecords() throws Exception {
        var started = runner.start("alice", FailureMode.SLOW);
        started.finished().get(30, TimeUnit.SECONDS);
        var job = store.find(started.jobId(), "alice").orElseThrow();

        assertThat(job.status()).isEqualTo(Status.FAILED);
        assertThat(job.message()).startsWith("Timed out after 1 s.");
        assertThat(job.batches()).anyMatch(b -> b.status().equals("STOPPED"));
        assertThat(store.summary(job.id()).records()).isZero();
    }
}
