@param String user
@param String tab

@template.layout(title = "Step 5 · Chat", step = 5, content = @`
    <h1 class="text-2xl font-bold text-gray-900">Step 5: A chat room</h1>
    <p class="text-gray-700">
        Open this page in two or more tabs. Each tab has its own stream; the server keeps all of them in a list and
        broadcasts every message to each one. Sending a message is a normal POST.
    </p>

    <section class="bg-white rounded-lg border border-gray-200 p-6 space-y-4"
             hx-ext="sse" sse-connect="/step5/stream?tab=${tab}">
        <div class="flex items-center justify-between">
            <p class="text-sm text-gray-600">You are <b>${user}</b></p>
            <p class="text-xs bg-slate-700 text-white px-2 py-0.5 rounded" sse-swap="presence">connecting…</p>
        </div>

        <ul class="h-64 overflow-auto border border-gray-100 rounded p-3 space-y-1 text-sm" id="messages"
            sse-swap="message" hx-swap="beforeend"></ul>

        <form hx-post="/step5/messages" hx-swap="none" class="flex gap-2"
              hx-on::after-request="if (event.detail.successful) this.querySelector('[name=text]').value = ''">
            <input type="hidden" name="user" value="${user}"/>
            <input name="text" type="text" placeholder="Say something" autocomplete="off" required
                   class="flex-1 border border-gray-300 rounded px-3 py-1.5 text-sm"/>
            <button type="submit" class="border border-gray-400 bg-white hover:bg-gray-50 px-5 py-1.5 rounded text-sm">Send</button>
        </form>

        <div class="flex items-center gap-3">
            <button class="border border-gray-400 bg-white hover:bg-gray-50 px-3 py-1 rounded text-xs"
                    hx-post="/step5/tabs/${tab}/drop" hx-swap="none">
                Simulate a dropped connection
            </button>
            <span class="text-xs text-gray-500">then send a message from another tab within 5 seconds</span>
        </div>
        <pre id="log" class="bg-slate-900 text-green-300 text-xs p-3 rounded h-24 overflow-auto"></pre>
    </section>

    <section class="bg-blue-50 border border-blue-200 rounded-lg p-5 text-sm text-gray-800 space-y-2">
        <h2 class="font-semibold">What to notice</h2>
        <ul class="list-disc pl-5 space-y-1">
            <li>The <code>presence</code> count changes as tabs open and close. The server removes a tab when its stream
                completes or when a <code>send</code> to it fails.</li>
            <li>Every 15 s the server sends a comment line (<code>: ping</code>). The browser ignores it, but the write
                tells the server whether the tab is still there.</li>
            <li>Every message event has an <code>id</code>. Press "Simulate a dropped connection", post from another tab,
                and watch this tab reconnect after 5 s (the <code>retry</code> the server sent) and receive only the
                missed messages. The browser sends the last id in the <code>Last-Event-ID</code> header.</li>
            <li>jte escapes the output of every expression in HTML by default, so HTML typed into the chat is shown as text.
                Broadcasting unescaped user input would be an XSS hole for every connected tab.</li>
        </ul>
    </section>

    <section class="bg-white rounded-lg border border-gray-200 p-5 text-sm space-y-2">
        <h2 class="font-semibold">Try it with curl</h2>
        <pre class="bg-gray-100 p-3 rounded text-xs">curl -N "http://localhost:8080/step5/stream?tab=curl"
curl -N -H "Last-Event-ID: 3" "http://localhost:8080/step5/stream?tab=curl"   # replay after message 3
curl -d user=curl -d text=hello http://localhost:8080/step5/messages</pre>
    </section>

    <p class="text-sm flex justify-between">
        <a href="/step4" class="text-blue-700 hover:underline">← Step 4</a>
        <a href="/step6" class="text-blue-700 hover:underline">Next: Step 6 →</a>
    </p>

    <script>
        function log(text) {
            const el = document.getElementById('log');
            el.textContent += new Date().toLocaleTimeString() + '  ' + text + '\n';
            el.scrollTop = el.scrollHeight;
        }
        document.body.addEventListener('htmx:sseOpen', () => log('stream connected'));
        document.body.addEventListener('htmx:sseError', () => log('stream dropped, the browser reconnects with Last-Event-ID'));
        // Keep the newest message in view
        document.body.addEventListener('htmx:sseMessage', e => {
            if (e.detail.type === 'message') {
                const list = document.getElementById('messages');
                list.scrollTop = list.scrollHeight;
            }
        });
    </script>
`)
