@import java.time.LocalTime

@param String task
@param LocalTime finishedAt

<%-- Sent as the data of the "done" event when the job succeeds --%>
<div class="bg-green-50 border border-green-200 rounded p-3 text-sm text-green-800">
    Finished <b>${task}</b> at ${finishedAt.toString()}.
</div>
