package com.example.sse;

import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Renders a jte template to an HTML string, so it can be sent as the data of an SSE event. htmx then swaps that
 * HTML into the page. Introduced in step 3.
 */
@Component
public class FragmentRenderer {

    private final TemplateEngine templateEngine;

    public FragmentRenderer(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * @param template the template path under {@code src/main/jte}, without {@code .jte}, e.g. {@code "step3/progress"}
     * @param params   the template's {@code @param}s by name
     */
    public String render(String template, Map<String, Object> params) {
        var output = new StringOutput();
        templateEngine.render(template + ".jte", params, output);
        // Put the HTML on one line. SSE ends a data field at each line break, so this keeps the event easy to
        // read on the wire (curl -N) and avoids depending on how multi-line data gets split into data: lines.
        return output.toString().strip().replaceAll("\\s*\\R\\s*", " ");
    }
}
