@import java.time.LocalTime

@param String name
@param LocalTime time

<%-- Data of each "step" event --%>
<li class="text-gray-700">
    <span class="text-green-600">✓</span> ${name}
    <span class="text-xs text-gray-400">${time.toString()}</span>
</li>
