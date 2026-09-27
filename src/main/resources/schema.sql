-- Steps 7 and 8. Spring Boot runs this file at startup for an embedded database such as H2.
-- IF NOT EXISTS keeps it safe to rerun against a database that survives restarts.
CREATE TABLE IF NOT EXISTS records (
    id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    batch  INT         NOT NULL,
    amount INT         NOT NULL,
    status VARCHAR(10) NOT NULL,  -- NEW after insert, PROCESSED after processing
    result INT
);

CREATE INDEX IF NOT EXISTS records_job_status ON records (job_id, status);

-- Step 8: the state of every job lives here, not in the server's memory
CREATE TABLE IF NOT EXISTS jobs (
    id             VARCHAR(36)  PRIMARY KEY,
    owner          VARCHAR(50)  NOT NULL,
    failure_mode   VARCHAR(20)  NOT NULL,
    status         VARCHAR(12)  NOT NULL,  -- INSERTING, PROCESSING, DONE, FAILED, CANCELLED
    stop_requested VARCHAR(10),            -- CANCEL, TIMEOUT or FAILED: asks every thread of the job to stop
    message        VARCHAR(500),
    processed      INT          NOT NULL DEFAULT 0,
    total_rows     INT          NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    finished_at    TIMESTAMP
);

CREATE TABLE IF NOT EXISTS job_batches (
    job_id   VARCHAR(36) NOT NULL,
    batch    INT         NOT NULL,
    status   VARCHAR(10) NOT NULL,  -- WAITING, RUNNING, RETRYING, DONE, FAILED, STOPPED
    inserted INT         NOT NULL DEFAULT 0,
    attempts INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (job_id, batch)
);
