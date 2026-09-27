package com.example.sse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser("alice") // every test runs logged in as a plain user (role USER), unless it says otherwise
class SseTutorialApplicationTests {

    @Autowired
    MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/step1", "/step2", "/step3", "/step5", "/step7", "/step8"})
    void userPagesRender(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/step1", "/step2", "/step3", "/step4", "/step5", "/step6", "/step7", "/step8"})
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    void adminSeesEveryPage(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/step4", "/step4/stream", "/step6", "/step6/jobs/any/events"})
    void adminPagesAndStreamsAreForbiddenForUsers(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isForbidden());
    }

    @Test
    void forbiddenPageShowsTheAccessDeniedPage() throws Exception {
        // MockMvc doesn't forward to /error by itself, so render the error page the way Boot does after a 403
        mvc.perform(get("/error").requestAttr("jakarta.servlet.error.status_code", 403)
                        .requestAttr("jakarta.servlet.error.request_uri", "/step4")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("You can't open this page")));
    }

    @Test
    void adminPostsAreForbiddenForUsers() throws Exception {
        mvc.perform(post("/step6/jobs").param("version", "1.0").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/step6/jobs/any/answer").param("approved", "true").with(csrf())).andExpect(status().isForbidden());
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
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    void unknownJobStreamIsNotFound() throws Exception {
        mvc.perform(get("/step3/jobs/does-not-exist/events")).andExpect(status().isNotFound());
        mvc.perform(get("/step6/jobs/does-not-exist/events")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/step1", "/step2/stream", "/step4/stream", "/step5/stream?tab=x"})
    @WithAnonymousUser
    void pagesAndStreamsNeedALogin(String path) throws Exception {
        mvc.perform(get(path).accept(MediaType.TEXT_HTML, MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void postsNeedTheCsrfToken() throws Exception {
        mvc.perform(post("/step5/messages").param("text", "hi")).andExpect(status().isForbidden());
        mvc.perform(post("/step5/messages").param("text", "hi").with(csrf())).andExpect(status().isNoContent());
    }
}
