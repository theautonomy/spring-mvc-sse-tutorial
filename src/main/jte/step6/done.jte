@param boolean success
@param String message

<%-- Data of the final "done" event --%>
<div class="rounded p-3 text-sm ${success ? "bg-green-50 border border-green-200 text-green-800" : "bg-gray-50 border border-gray-200 text-gray-700"}">
    ${message}
</div>
