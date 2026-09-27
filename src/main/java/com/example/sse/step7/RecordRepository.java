package com.example.sse.step7;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Step 7: the records table (see schema.sql). Safe to call from several threads at once: every call borrows its own
 * connection from the pool.
 */
@Repository
class RecordRepository {

    record Summary(int inserted, int processed, long total) {}

    private final JdbcClient jdbc;

    RecordRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(String jobId, int batch, List<Integer> amounts) {
        for (var amount : amounts) {
            jdbc.sql("INSERT INTO records (job_id, batch, amount, status) VALUES (?, ?, ?, 'NEW')")
                    .params(jobId, batch, amount)
                    .update();
        }
    }

    List<Long> findNew(String jobId, int limit) {
        return jdbc.sql("SELECT id FROM records WHERE job_id = ? AND status = 'NEW' ORDER BY id LIMIT ?")
                .params(jobId, limit)
                .query(Long.class)
                .list();
    }

    /** "Processing" a record: compute its result and mark it done. */
    void process(List<Long> ids) {
        jdbc.sql("UPDATE records SET result = amount * 2, status = 'PROCESSED' WHERE id IN (:ids)")
                .param("ids", ids)
                .update();
    }

    Summary summary(String jobId) {
        return jdbc.sql("""
                        SELECT COUNT(*) AS inserted, COUNT(result) AS processed, COALESCE(SUM(result), 0) AS total
                        FROM records WHERE job_id = ?""")
                .param(jobId)
                .query(Summary.class)
                .single();
    }
}
