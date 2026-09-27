@param String label
@param int percent

<%-- Data of the "cpu" and "memory" events --%>
<div>
    <p class="text-xs font-semibold uppercase tracking-wider text-gray-500">${label}</p>
    <p class="text-3xl font-bold text-gray-900">${percent}%</p>
    <div class="w-full bg-gray-200 rounded h-2 mt-2 overflow-hidden">
        <div class="h-2 transition-all duration-500 ${percent > 80 ? "bg-red-500" : "bg-slate-700"}" style="width: ${percent}%"></div>
    </div>
</div>
