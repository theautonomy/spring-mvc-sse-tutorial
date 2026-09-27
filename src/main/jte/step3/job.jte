@param String jobId
@param String task

<%-- Returned by POST /step3/jobs. Inserting it opens the job's stream. --%>
<div class="border border-gray-200 rounded p-4 space-y-3"
     hx-ext="sse" sse-connect="/step3/jobs/${jobId}/events" sse-close="done">
    <p class="text-sm text-gray-700">
        <span class="font-semibold">${task}</span>
        <span class="text-xs text-gray-400">job ${jobId}</span>
    </p>
    <div sse-swap="progress">
        <p class="text-sm text-gray-500">Waiting for the first event…</p>
    </div>
    <div sse-swap="done"></div>
</div>
