-- Step 7. Spring Boot runs this file at startup for an embedded database such as H2.
CREATE TABLE records (
    id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    batch  INT         NOT NULL,
    amount INT         NOT NULL,
    status VARCHAR(10) NOT NULL,  -- NEW after insert, PROCESSED after processing
    result INT
);

CREATE INDEX records_job_status ON records (job_id, status);
