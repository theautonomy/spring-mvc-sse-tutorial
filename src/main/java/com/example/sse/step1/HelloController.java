package com.example.sse.step1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Step 1: the smallest possible SSE endpoint. It sends one message, one named event, and ends the stream.
 */
@Controller
class HelloController {

    private static final Logger log = LoggerFactory.getLogger(HelloController.class);

    private final AtomicInteger connections = new AtomicInteger();

    @GetMapping("/step1")
    String page() {
        return "step1";
    }

    @GetMapping(path = "/step1/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream() throws IOException {
        var connection = connections.incrementAndGet();
        log.info("Step 1: connection #{}", connection);

        var emitter = new SseEmitter();
        // Sending before the method returns is fine: the emitter buffers events until Spring MVC has set up
        // the response, then flushes them.
        emitter.send("Hello, world #" + connection);                  // data:Hello, world #1
        emitter.send(SseEmitter.event().name("close").data("bye"));   // event:close + data:bye
        emitter.complete();                                           // the server closes the connection
        return emitter;
    }
}
