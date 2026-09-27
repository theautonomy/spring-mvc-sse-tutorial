@param String jobId
@param String version
@param long timeoutSeconds

<%-- Data of the "question" event. The buttons answer with a normal POST. --%>
<div class="bg-amber-50 border border-amber-200 rounded p-4 space-y-3">
    <p class="text-sm font-medium text-gray-800">
        Staging looks good. Deploy <b>${version}</b> to production?
    </p>
    <p class="text-xs text-gray-500">The job waits up to ${timeoutSeconds} s for an answer.</p>
    <div class="flex gap-2">
        <button class="bg-slate-700 hover:bg-slate-800 text-white px-4 py-1.5 rounded text-sm"
                hx-post="/step6/jobs/${jobId}/answer" hx-vals='{"approved": true}' hx-target="#question">
            Approve
        </button>
        <button class="border border-gray-400 bg-white hover:bg-gray-50 px-4 py-1.5 rounded text-sm"
                hx-post="/step6/jobs/${jobId}/answer" hx-vals='{"approved": false}' hx-target="#question">
            Reject
        </button>
    </div>
</div>
