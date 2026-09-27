package com.example.sse.step7;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Runs the real pipeline against the in-memory database and reads the final "done" event. */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser("alice")
class PipelineTests {

    private static final Pattern EVENTS_URL = Pattern.compile("data-events=\"([^\"]+)\"");

    @Autowired
    MockMvc mvc;

    @Test
    void processesEveryRecordAfterAllInsertsFinished() throws Exception {
        var stream = runJob("dontWait", "false");

        assertThat(stream).contains("event:insert", "event:process");
        // Processing only starts after the last insert
        assertThat(stream.lastIndexOf("event:insert")).isLessThan(stream.indexOf("event:process"));
        assertThat(doneEvent(stream)).contains("<b>200</b> records inserted, <b>200</b> processed");
    }

    @Test
    void failedBatchMeansProcessingNeverRuns() throws Exception {
        var stream = runJob("failBatch", "true");

        assertThat(stream).doesNotContain("event:process");
        assertThat(doneEvent(stream)).contains("Batch 3 failed after 20 rows").contains("<b>0</b> processed");
    }

    @Test
    void unknownJobIsNotFound() throws Exception {
        mvc.perform(get("/step7/jobs/does-not-exist/events")).andExpect(status().isNotFound());
    }

    /** Starts a job, connects to its stream, and returns everything sent until the "done" event. */
    private String runJob(String option, String value) throws Exception {
        var job = mvc.perform(post("/step7/jobs").param(option, value).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var matcher = EVENTS_URL.matcher(job);
        assertThat(matcher.find()).isTrue();

        var response = mvc.perform(get(matcher.group(1)))
                .andExpect(request().asyncStarted())
                .andReturn().getResponse();

        // The pipeline writes to the response from its own threads; wait for the last event
        var deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (!response.getContentAsString().contains("event:done") && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }
        return response.getContentAsString();
    }

    private static String doneEvent(String stream) {
        assertThat(stream).contains("event:done");
        return stream.substring(stream.indexOf("event:done"));
    }
}
