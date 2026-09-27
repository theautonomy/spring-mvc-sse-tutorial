@import java.util.List

@param int step

!{var labels = List.of("Hello world", "Clock", "Progress", "Dashboard", "Chat", "Approval");}
<nav class="bg-slate-800 px-6 py-4 flex flex-wrap items-center gap-x-5 gap-y-2">
    <a href="/" class="text-xl font-bold text-white mr-4">SSE Tutorial</a>
    @for(int i = 1; i <= labels.size(); i++)
        <a href="/step${i}" class="text-sm ${i == step ? "text-white font-semibold" : "text-slate-400 hover:text-white"}">${i} ${labels.get(i - 1)}</a>
    @endfor
</nav>
