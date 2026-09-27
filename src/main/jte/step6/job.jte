@param String jobId
@param String version

<%-- Returned by POST /step6/jobs. Inserting it opens the job's stream. --%>
<div class="border border-gray-200 rounded p-4 space-y-3"
     hx-ext="sse" sse-connect="/step6/jobs/${jobId}/events" sse-close="done">
    <p class="text-sm text-gray-700">Deploying <b>${version}</b></p>
    <ol class="text-sm space-y-1" sse-swap="step" hx-swap="beforeend"></ol>
    <div id="question" sse-swap="question"></div>
    <div sse-swap="done"></div>
</div>
