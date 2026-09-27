@import java.time.LocalTime

@param LocalTime time
@param String text

<%-- Data of each "log" event --%>
<li><span class="text-gray-400">${time.toString()}</span> <span>${text}</span></li>
