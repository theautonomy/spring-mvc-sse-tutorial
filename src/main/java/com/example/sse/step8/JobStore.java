package com.example.sse.step8;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Step 8: all state of a job lives in the database (see schema.sql). Worker threads write it, and the SSE stream
 * reads it, so the stream doesn't need to be on the thread, or even the server, that runs the job.
 */
@Repository
class JobStore {

    enum Status {
        INSERTING, PROCESSING, DONE, FAILED, CANCELLED;

        public boolean finished() { // public: the template calls it
            return this == DONE || this == FAILED || this == CANCELLED;
        }
    }

    record Batch(int batch, String status, int inserted, int attempts) {}

    record Job(String id, String owner, String failureMode, Status status, @Nullable String stopRequested,
               @Nullable String message, int processed, int totalRows, LocalDateTime createdAt,
               @Nullable LocalDateTime finishedAt, List<Batch> batches) {}

    record Summary(int records, int processed, long total) {}

    /** Why the job was asked to stop (CANCEL, TIMEOUT or FAILED), and the message for the user. */
    record Stop(String reason, String message) {}

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    JobStore(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    // --- jobs ---------------------------------------------------------------------------------------------------

    /** Call inside a transaction: the job and its batch rows appear together or not at all. */
    void create(String jobId, String owner, String failureMode, int batches, int rowsPerBatch) {
        jdbc.sql("""
                        INSERT INTO jobs (id, owner, failure_mode, status, total_rows, created_at)
                        VALUES (?, ?, ?, 'INSERTING', ?, CURRENT_TIMESTAMP)""")
                .params(jobId, owner, failureMode, batches * rowsPerBatch)
                .update();
        for (int batch = 0; batch < batches; batch++) {
            jdbc.sql("INSERT INTO job_batches (job_id, batch, status) VALUES (?, ?, 'WAITING')")
                    .params(jobId, batch)
                    .update();
        }
    }

    /** Only the owner finds a job. For anyone else it doesn't exist, which the controller turns into a 404. */
    Optional<Job> find(String jobId, String owner) {
        return jdbc.sql("""
                        SELECT id, owner, failure_mode, status, stop_requested, message, processed, total_rows,
                               created_at, finished_at
                        FROM jobs WHERE id = ? AND owner = ?""")
                .params(jobId, owner)
                .query((rs, row) -> new Job(rs.getString("id"), rs.getString("owner"), rs.getString("failure_mode"),
                        Status.valueOf(rs.getString("status")), rs.getString("stop_requested"),
                        rs.getString("message"), rs.getInt("processed"), rs.getInt("total_rows"),
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getObject("finished_at", LocalDateTime.class), batches(jobId)))
                .optional();
    }

    List<Job> recent(String owner, int limit) {
        var ids = jdbc.sql("SELECT id FROM jobs WHERE owner = ? ORDER BY created_at DESC LIMIT ?")
                .params(owner, limit)
                .query(String.class)
                .list();
        return ids.stream().flatMap(id -> find(id, owner).stream()).toList();
    }

    private List<Batch> batches(String jobId) {
        return jdbc.sql("SELECT batch, status, inserted, attempts FROM job_batches WHERE job_id = ? ORDER BY batch")
                .param(jobId)
                .query(Batch.class)
                .list();
    }

    void setStatus(String jobId, Status status) {
        jdbc.sql("UPDATE jobs SET status = ? WHERE id = ?").params(status.name(), jobId).update();
    }

    void finish(String jobId, Status status, @Nullable String message) {
        jdbc.sql("UPDATE jobs SET status = ?, message = ?, finished_at = CURRENT_TIMESTAMP WHERE id = ?")
                .params(status.name(), message, jobId)
                .update();
    }

    /**
     * Asks every thread of the job to stop at its next check. Only the first reason counts: if a batch fails and
     * the user presses cancel a moment later, the job still ends as "failed".
     *
     * @return {@code false} if a stop had already been requested
     */
    boolean requestStop(String jobId, String reason, @Nullable String message) {
        return jdbc.sql("UPDATE jobs SET stop_requested = ?, message = ? WHERE id = ? AND stop_requested IS NULL")
                .params(reason, message, jobId)
                .update() == 1;
    }

    Optional<Stop> stop(String jobId) {
        return jdbc.sql("SELECT stop_requested AS reason, message FROM jobs WHERE id = ? AND stop_requested IS NOT NULL")
                .param(jobId)
                .query(Stop.class)
                .optional();
    }

    /** For the startup sweep: jobs a previous run of the server left unfinished. */
    int failUnfinished(String message) {
        jdbc.sql("DELETE FROM records WHERE job_id IN (SELECT id FROM jobs WHERE status IN ('INSERTING', 'PROCESSING'))")
                .update();
        return jdbc.sql("""
                        UPDATE jobs SET status = 'FAILED', message = ?, finished_at = CURRENT_TIMESTAMP
                        WHERE status IN ('INSERTING', 'PROCESSING')""")
                .param(message)
                .update();
    }

    // --- batches ------------------------------------------------------------------------------------------------

    void setBatch(String jobId, int batch, String status) {
        jdbc.sql("UPDATE job_batches SET status = ? WHERE job_id = ? AND batch = ?")
                .params(status, jobId, batch)
                .update();
    }

    void startAttempt(String jobId, int batch) {
        jdbc.sql("UPDATE job_batches SET status = 'RUNNING', inserted = 0, attempts = attempts + 1 WHERE job_id = ? AND batch = ?")
                .params(jobId, batch)
                .update();
    }

    void setInserted(String jobId, int batch, int inserted) {
        jdbc.sql("UPDATE job_batches SET inserted = ? WHERE job_id = ? AND batch = ?")
                .params(inserted, jobId, batch)
                .update();
    }

    // --- records ------------------------------------------------------------------------------------------------

    /** One JDBC batch: a single round trip for all rows, instead of one INSERT each (step 7). */
    void insertRecords(String jobId, int batch, List<Integer> amounts) {
        jdbcTemplate.batchUpdate("INSERT INTO records (job_id, batch, amount, status) VALUES (?, ?, ?, 'NEW')",
                amounts, amounts.size(), (ps, amount) -> {
                    ps.setString(1, jobId);
                    ps.setInt(2, batch);
                    ps.setInt(3, amount);
                });
    }

    List<Long> findNew(String jobId, int limit) {
        return jdbc.sql("SELECT id FROM records WHERE job_id = ? AND status = 'NEW' ORDER BY id LIMIT ?")
                .params(jobId, limit)
                .query(Long.class)
                .list();
    }

    /** Call inside a transaction: the records and the job's counter change together. */
    void process(String jobId, List<Long> ids) {
        jdbc.sql("UPDATE records SET result = amount * 2, status = 'PROCESSED' WHERE id IN (:ids)")
                .param("ids", ids)
                .update();
        jdbc.sql("UPDATE jobs SET processed = processed + ? WHERE id = ?").params(ids.size(), jobId).update();
    }

    int deleteRecords(String jobId) {
        return jdbc.sql("DELETE FROM records WHERE job_id = ?").param(jobId).update();
    }

    Summary summary(String jobId) {
        return jdbc.sql("""
                        SELECT COUNT(*) AS records, COUNT(result) AS processed, COALESCE(SUM(result), 0) AS total
                        FROM records WHERE job_id = ?""")
                .param(jobId)
                .query(Summary.class)
                .single();
    }
}
