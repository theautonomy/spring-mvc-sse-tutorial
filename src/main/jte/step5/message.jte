@import java.time.LocalTime

@param long id
@param String user
@param String text
@param LocalTime time

<%-- Data of each "message" event. jte escapes ${user} and ${text}, so typed HTML shows as text. --%>
<li>
    <span class="text-xs text-gray-400">#${id} ${time.toString()}</span>
    <b class="text-gray-800">${user}</b>
    <span class="text-gray-700">${text}</span>
</li>
