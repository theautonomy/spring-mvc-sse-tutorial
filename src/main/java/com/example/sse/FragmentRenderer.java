package com.example.sse;

import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;
import java.util.Set;

/**
 * Renders one {@code th:fragment} of a Thymeleaf template to an HTML string, so it can be sent as the data of an
 * SSE event. The page then puts that HTML into the right element. Introduced in step 3.
 */
@Component
public class FragmentRenderer {

    private final ITemplateEngine templateEngine;

    public FragmentRenderer(ITemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String render(String template, String fragment, Map<String, Object> variables) {
        var context = new Context();
        context.setVariables(variables);
        var html = templateEngine.process(template, Set.of(fragment), context);
        // Put the HTML on one line. SSE ends a data field at each line break, so this keeps the event easy to
        // read on the wire (curl -N) and avoids depending on how multi-line data gets split into data: lines.
        return html.strip().replaceAll("\\s*\\R\\s*", " ");
    }
}
