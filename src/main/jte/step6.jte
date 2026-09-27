@template.layout(title = "Step 6 · Approval", step = 6, content = @`
    <h1 class="text-2xl font-bold text-gray-900">Step 6: Ask the user</h1>
    <p class="text-gray-700">
        A fake deployment runs a few steps, then stops and asks for approval. The question arrives over SSE; your
        answer goes back as a normal POST. The job thread waits in between, and continues once the answer is in.
    </p>

    <section class="bg-white rounded-lg border border-gray-200 p-6 space-y-4">
        <form hx-post="/step6/jobs" hx-target="#job" hx-swap="innerHTML" class="flex flex-wrap items-end gap-3">
            <div class="flex-1 min-w-48">
                <label class="block text-xs font-medium text-gray-600 mb-1" for="version">Version to deploy</label>
                <input id="version" name="version" type="text" value="1.4.2" required
                       class="border border-gray-300 rounded px-3 py-1.5 text-sm w-full"/>
            </div>
            <button type="submit" class="border border-gray-400 bg-white hover:bg-gray-50 px-5 py-1.5 rounded text-sm">Deploy</button>
        </form>
        <div id="job"></div>
    </section>

    <section class="bg-blue-50 border border-blue-200 rounded-lg p-5 text-sm text-gray-800 space-y-2">
        <h2 class="font-semibold">What to notice</h2>
        <ul class="list-disc pl-5 space-y-1">
            <li>SSE is one-way. The "two-way" part is a second channel: the answer is an ordinary
                <code>POST /step6/jobs/{id}/answer</code>.</li>
            <li>The job thread blocks in <code>PendingQuestion.await</code> (a <code>CompletableFuture</code>).
                The POST handler completes that future, which wakes the job up.</li>
            <li>Try all three outcomes: approve, reject, and doing nothing for 60 s. After a timeout the question is
                expired, so a late click gets "too late" instead of being silently ignored.</li>
            <li>This is exactly how the nutrition planner asks its questions: <code>AskUserQuestionHandler</code> is the
                future, <code>fragments/hitl</code> is the question, <code>provideAnswers</code> is the POST.</li>
        </ul>
    </section>

    <p class="text-sm flex justify-between">
        <a href="/step5" class="text-blue-700 hover:underline">← Step 5</a>
        <a href="/" class="text-blue-700 hover:underline">Back to the start</a>
    </p>
`)
