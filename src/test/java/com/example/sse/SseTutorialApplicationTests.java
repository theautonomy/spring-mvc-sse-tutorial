package com.example.sse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SseTutorialApplicationTests {

    @Autowired
    MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/step1", "/step2", "/step3", "/step4", "/step5", "/step6"})
    void pagesRender(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    void step1StreamSendsHelloAndCloseEvent() throws Exception {
        var result = mvc.perform(get("/step1/stream"))
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .containsPattern("data:Hello, world #\\d+\n\n")
                .contains("event:close\ndata:bye\n\n");
    }

    @Test
    void unknownJobStreamIsNotFound() throws Exception {
        mvc.perform(get("/step3/jobs/does-not-exist/events")).andExpect(status().isNotFound());
        mvc.perform(get("/step6/jobs/does-not-exist/events")).andExpect(status().isNotFound());
    }
}
