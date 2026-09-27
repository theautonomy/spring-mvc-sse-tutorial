@import gg.jte.Content

@param String title
@param int step
@param Content content

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
    <title>${title} · SSE Tutorial</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <!-- htmx core + the SSE extension (hx-ext="sse", sse-connect, sse-swap, sse-close) -->
    <script src="https://unpkg.com/htmx.org@2.0.3"></script>
    <script src="https://unpkg.com/htmx-ext-sse@2.2.2/sse.js"></script>
</head>
<body class="bg-gray-100 min-h-screen font-sans">
@template.nav(step = step)
<main class="max-w-3xl mx-auto py-8 px-4 space-y-6">
    ${content}
</main>
</body>
</html>
