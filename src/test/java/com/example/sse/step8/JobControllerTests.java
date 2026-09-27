package com.example.sse.step8;

import com.example.sse.step8.JobRunner.FailureMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser("alice")
class JobControllerTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JobRunner runner;

    @Test
    void streamSendsStateUntilTheJobIsDone() throws Exception {
        var started = runner.start("alice", FailureMode.NONE);

        var response = mvc.perform(get("/step8/jobs/{id}/events", started.jobId()))
                .andExpect(request().asyncStarted())
                .andReturn().getResponse();
        var deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (!response.getContentAsString().contains("event:done") && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }

        var stream = response.getContentAsString();
        assertThat(stream).contains("event:state");
        assertThat(stream.substring(stream.indexOf("event:done"))).contains("DONE").contains("<b>600</b> records");
    }

    @Test
    void finishedJobCanBeReopenedLater() throws Exception {
        var started = runner.start("alice", FailureMode.NONE);
        started.finished().get(30, TimeUnit.SECONDS);

        // A reload after the job finished: the page renders the shell, and the stream sends "done" right away
        mvc.perform(get("/step8").param("job", started.jobId()))
                .andExpect(content().string(containsString("data-events=\"/step8/jobs/" + started.jobId() + "/events\"")));
        var response = mvc.perform(get("/step8/jobs/{id}/events", started.jobId())).andReturn().getResponse();
        var deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (!response.getContentAsString().contains("event:done") && Instant.now().isBefore(deadline)) {
            Thread.sleep(50);
        }
        assertThat(response.getContentAsString()).startsWith("event:done").doesNotContain("event:state");
    }

    @Test
    @WithMockUser("bob")
    void someoneElsesJobIsNotFound() throws Exception {
        var started = runner.start("alice", FailureMode.NONE);

        mvc.perform(get("/step8/jobs/{id}/events", started.jobId())).andExpect(status().isNotFound());
        mvc.perform(post("/step8/jobs/{id}/cancel", started.jobId()).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/step8").param("job", started.jobId()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(started.jobId()))));
        started.finished().get(30, TimeUnit.SECONDS);
    }

    @Test
    void startReturnsTheShellThatPointsToTheStream() throws Exception {
        mvc.perform(post("/step8/jobs").param("mode", "NONE").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-events=\"/step8/jobs/")))
                .andExpect(content().string(containsString("data-page=\"/step8?job=")));
    }
}
