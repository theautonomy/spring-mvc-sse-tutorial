@param boolean accepted
@param boolean approved

<%-- Response of POST /step6/jobs/{id}/answer --%>
<p class="text-sm text-gray-600">
    @if(!accepted)
        Too late: this question has already been answered or has expired.
    @elseif(approved)
        You approved. Continuing…
    @else
        You rejected.
    @endif
</p>
