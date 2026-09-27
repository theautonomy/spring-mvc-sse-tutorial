@param int percent
@param String message

<%-- Sent as the data of each "progress" event --%>
<div>
    <div class="w-full bg-gray-200 rounded h-3 overflow-hidden">
        <div class="bg-slate-700 h-3 transition-all duration-300" style="width: ${percent}%"></div>
    </div>
    <p class="text-xs text-gray-600 mt-1">${message} · ${percent}%</p>
</div>
