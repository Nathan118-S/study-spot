package education.studyspot.app.data

import androidx.annotation.VisibleForTesting
import education.studyspot.app.core.ApiClient
import education.studyspot.app.core.AppJson
import education.studyspot.app.core.Dates
import education.studyspot.app.core.Entities
import education.studyspot.app.core.doubleValue
import education.studyspot.app.core.get
import education.studyspot.app.core.userMessage
import education.studyspot.app.logic.AssignmentEdits
import education.studyspot.app.logic.CalendarMath
import education.studyspot.app.model.AppNotification
import education.studyspot.app.model.Assignment
import education.studyspot.app.model.AssignmentTemplate
import education.studyspot.app.model.Connections
import education.studyspot.app.model.IcalAddResult
import education.studyspot.app.model.IcalFeed
import education.studyspot.app.model.IcalFeeds
import education.studyspot.app.model.IcalSyncResult
import education.studyspot.app.model.Priority
import education.studyspot.app.model.SchoolClass
import education.studyspot.app.model.Source
import education.studyspot.app.model.StudySession
import education.studyspot.app.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId

/**
 * The single source of truth for the signed-in user's data, shared by every
 * screen (mirrors ios/StudySpot/State/DataStore.swift). Feature screens read
 * the [StateFlow]s and call the mutation methods here instead of talking to
 * [ApiClient] themselves.
 *
 * Mutations are optimistic like the web and iOS apps: the flow updates at once
 * and rolls back (with an error message via [MessageCenter]) if the server refuses.
 * Demo accounts never write to the server.
 */
class DataRepository(
    private val api: ApiClient,
    private val messages: MessageCenter,
    private val scope: CoroutineScope,
) {
    private val _user = MutableStateFlow<User?>(null)
    /** The signed-in user (GET /api/auth/me). Written by AuthStore and [updateUser]. */
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _assignments = MutableStateFlow<List<Assignment>>(emptyList())
    val assignments: StateFlow<List<Assignment>> = _assignments.asStateFlow()

    private val _classes = MutableStateFlow<List<SchoolClass>>(emptyList())
    val classes: StateFlow<List<SchoolClass>> = _classes.asStateFlow()

    private val _templates = MutableStateFlow<List<AssignmentTemplate>>(emptyList())
    val templates: StateFlow<List<AssignmentTemplate>> = _templates.asStateFlow()

    private val _studySessions = MutableStateFlow<List<StudySession>>(emptyList())
    val studySessions: StateFlow<List<StudySession>> = _studySessions.asStateFlow()

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _connections = MutableStateFlow(Connections())
    val connections: StateFlow<Connections> = _connections.asStateFlow()

    private val _icalFeeds = MutableStateFlow<List<IcalFeed>>(emptyList())
    /** Pasted Google Calendar links (Settings > Google). Loaded by [refreshIcalFeeds]. */
    val icalFeeds: StateFlow<List<IcalFeed>> = _icalFeeds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    /** True during the first load (before [hasLoaded]). */
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    /** Pull-to-refresh / sync in progress. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow("")
    val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    val unreadCount: StateFlow<Int> = notifications
        .map { list -> list.count { !it.read } }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    private val isDemo: Boolean get() = _user.value?.isDemo == true

    // region Lookups

    fun classFor(classId: String?): SchoolClass? = classId?.let { id -> _classes.value.firstOrNull { it.id == id } }

    /** The class color ("#rrggbb") for an assignment's class, or a neutral grey. */
    fun colorFor(classId: String?): String = classFor(classId)?.color ?: SchoolClass.UNCATEGORIZED_COLOR

    fun assignment(id: String): Assignment? = _assignments.value.firstOrNull { it.id == id }

    // endregion

    // region Session

    /** Called by AuthStore. */
    fun setUser(user: User?) {
        _user.value = user
    }

    fun reset() {
        _user.value = null
        _assignments.value = emptyList()
        _classes.value = emptyList()
        _templates.value = emptyList()
        _studySessions.value = emptyList()
        _notifications.value = emptyList()
        _connections.value = Connections()
        _icalFeeds.value = emptyList()
        _hasLoaded.value = false
        _syncMessage.value = ""
        _lastError.value = null
    }

    /** PATCH /api/auth/me (settings live in user.data; unknown keys go there). */
    suspend fun updateUser(patch: JsonObject): User {
        val updated = api.updateMe(patch)
        _user.value = updated
        return updated
    }

    suspend fun refreshUser() {
        runCatching { api.me() }.onSuccess { _user.value = it }
    }

    /**
     * Fills the flows directly, as if [load] had run. For screenshot tests and
     * previews; the app itself never calls it.
     */
    @VisibleForTesting
    internal fun seed(
        assignments: List<Assignment> = _assignments.value,
        classes: List<SchoolClass> = _classes.value,
        connections: Connections = _connections.value,
        notifications: List<AppNotification> = _notifications.value,
        templates: List<AssignmentTemplate> = _templates.value,
        studySessions: List<StudySession> = _studySessions.value,
    ) {
        _assignments.value = assignments
        _classes.value = classes
        _connections.value = connections
        _notifications.value = notifications
        _templates.value = templates
        _studySessions.value = studySessions
        _hasLoaded.value = true
    }

    // endregion

    // region Loading

    /** Initial load after sign-in: assignments, classes, then connections, notifications and templates. */
    suspend fun load() {
        if (isDemo) {
            _assignments.value = DemoData.assignments
            _classes.value = DemoData.classes
            _connections.value = DemoData.connections
            _hasLoaded.value = true
            return
        }
        if (!_hasLoaded.value) _isLoading.value = true
        try {
            coroutineScope {
                val a = async { api.assignments() }
                val c = async { api.classes() }
                _assignments.value = a.await()
                _classes.value = c.await()
            }
            _hasLoaded.value = true
            _lastError.value = null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _lastError.value = e.userMessage
        } finally {
            _isLoading.value = false
        }
        coroutineScope {
            listOf(
                async { refreshConnections() },
                async { loadNotifications() },
                async { loadTemplates() },
            ).awaitAll()
        }
    }

    /** Re-fetch assignments and classes (pull to refresh without connected services). */
    suspend fun reloadData() {
        if (isDemo) return
        coroutineScope {
            launch { runCatching { api.assignments() }.onSuccess { _assignments.value = it } }
            launch { runCatching { api.classes() }.onSuccess { _classes.value = it } }
        }
    }

    /** Pull to refresh: syncs connected services first, like iOS. */
    suspend fun refresh() {
        _isRefreshing.value = true
        try {
            if (_connections.value.any) runSync(silent = false) else reloadData()
        } finally {
            _isRefreshing.value = false
        }
    }

    suspend fun refreshConnections() {
        if (isDemo) { _connections.value = DemoData.connections; return }
        runCatching { api.connections() }.onSuccess { _connections.value = it }
    }

    suspend fun loadNotifications() {
        if (isDemo) return
        runCatching { api.notifications() }.onSuccess { _notifications.value = it }
    }

    suspend fun loadTemplates() {
        if (isDemo) return
        runCatching { api.templates() }.onSuccess { _templates.value = it }
    }

    suspend fun loadStudySessions() {
        if (isDemo) return
        runCatching { api.studySessions() }.onSuccess { _studySessions.value = it }
    }

    // endregion

    // region Sync (Google Calendar / Classroom / Blackboard / calendar links)

    /** Auto-refresh synced data when the last sync was more than 6 hours ago. */
    suspend fun autoSyncIfStale() {
        val u = _user.value ?: return
        if (u.isDemo || !_connections.value.any) return
        val last = u.lastSync
        if (last != null && Instant.now().epochSecond - last.epochSecond < 6 * 3600) return
        runSync(silent = true)
    }

    suspend fun runSync(silent: Boolean) {
        if (isDemo) return
        _isSyncing.value = true
        if (!silent) _syncMessage.value = "Syncing..."
        try {
            val c = _connections.value
            val services = buildList {
                if (c.calendar) add("Calendar" to "syncGoogleCalendar")
                if (c.classroom) add("Classroom" to "syncGoogleClassroom")
                if (c.blackboard) add("Blackboard" to "syncBlackboard")
                if (c.ical) add("Calendar links" to "icalSyncFeeds")
            }
            val results = coroutineScope {
                services.map { (label, fn) ->
                    async {
                        try {
                            val r = api.invoke(fn)
                            "$label: ${(r["imported"].doubleValue ?: 0.0).toInt()} new"
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            "$label: ${e.userMessage}"
                        }
                    }
                }.awaitAll()
            }
            if (!silent) _syncMessage.value = results.joinToString(" · ")
            reloadData()
            if (c.ical) refreshIcalFeeds()
            if (services.isNotEmpty()) {
                runCatching { updateUser(buildJsonObject { put("last_sync", Dates.format(Instant.now())) }) }
            }
        } finally {
            _isSyncing.value = false
        }
    }

    // endregion

    // region Calendar links (functions/ical*, server/src/functions/ical.js)

    suspend fun refreshIcalFeeds() {
        if (isDemo) { _icalFeeds.value = emptyList(); return }
        runCatching { api.invokeAs<IcalFeeds>("icalListFeeds") }.onSuccess { _icalFeeds.value = it.feeds }
    }

    /**
     * Adds a link and imports it (can take a few seconds). Throws the server's
     * user-ready message (bad link, school blocks it, duplicate, demo).
     */
    suspend fun addIcalFeed(url: String, label: String?, mode: String): IcalAddResult {
        val result = api.invokeAs<IcalAddResult>("icalAddFeed", buildJsonObject {
            put("url", url.trim())
            label?.trim()?.takeIf { it.isNotEmpty() }?.let { put("label", it) }
            put("mode", mode)
        })
        afterIcalChange()
        return result
    }

    /** Renames a link (blank clears the label) or switches its mode. Throws on failure. */
    suspend fun updateIcalFeed(id: String, label: String? = null, mode: String? = null) {
        val res = api.invokeAs<JsonObject>("icalUpdateFeed", buildJsonObject {
            put("id", id)
            label?.let { put("label", it.trim()) }
            mode?.let { put("mode", it) }
        })
        val feed = res["feed"]?.let { runCatching { AppJson.decodeFromJsonElement(IcalFeed.serializer(), it) }.getOrNull() }
        if (feed != null) _icalFeeds.update { list -> list.map { if (it.id == feed.id) feed else it } }
        // A rename renames the link's class; a mode change re-imports on the server.
        reloadData()
    }

    /** Removes a link; its imported assignments (and class) too when [removeAssignments]. Throws on failure. */
    suspend fun removeIcalFeed(id: String, removeAssignments: Boolean) {
        api.invoke("icalRemoveFeed", buildJsonObject {
            put("id", id)
            put("removeAssignments", removeAssignments)
        })
        _icalFeeds.update { list -> list.filterNot { it.id == id } }
        afterIcalChange()
    }

    /** Re-imports every link now. Throws on failure; per-link errors are in the result. */
    suspend fun syncIcalFeeds(): IcalSyncResult {
        val result = api.invokeAs<IcalSyncResult>("icalSyncFeeds")
        afterIcalChange()
        return result
    }

    private suspend fun afterIcalChange() {
        coroutineScope {
            launch { refreshIcalFeeds() }
            launch { refreshConnections() }
            launch { reloadData() }
        }
    }

    // endregion

    // region Assignments

    /** POST /api/entities/Assignment. [fields] uses server column names (title, class_id, due_date, …). */
    suspend fun createAssignment(fields: JsonObject): Assignment? {
        if (isDemo) return null
        return try {
            api.create<Assignment>(Entities.ASSIGNMENT, fields).also { created ->
                _assignments.update { listOf(created) + it }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            messages.show("Couldn't add the assignment", e.userMessage, error = true)
            null
        }
    }

    /**
     * PATCH an assignment. [optimistic] (if given) is shown immediately and
     * rolled back on failure. Returns the server's copy, or null on failure.
     */
    suspend fun updateAssignment(id: String, patch: JsonObject, optimistic: Assignment? = null): Assignment? {
        val prev = _assignments.value
        optimistic?.let(::apply)
        if (isDemo) return optimistic
        return try {
            api.updateAssignment(id, patch).also(::apply)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _assignments.value = prev
            messages.show("Couldn't save the assignment", e.userMessage, error = true)
            null
        }
    }

    /**
     * Marks [a] complete or not. With [withProgress] (the detail screen, like iOS)
     * progress and subtasks follow the new state too. Completing a Google Classroom
     * assignment also turns it in on Classroom when the user allows it.
     */
    suspend fun toggleComplete(a: Assignment, withProgress: Boolean = false) {
        val next = if (withProgress) AssignmentEdits.toggledCompletion(a) else a.copy(completed = !a.completed)
        val patch = buildJsonObject {
            put("completed", next.completed)
            if (withProgress) {
                put("progress", next.progress)
                if (a.subtasks.isNotEmpty()) put("subtasks", AssignmentEdits.subtasksJson(next.subtasks))
            }
        }
        val saved = updateAssignment(a.id, patch, next)
        if (saved != null && next.completed) syncCompletionToClassroom(listOf(a))
    }

    private fun syncCompletionToClassroom(completed: List<Assignment>) {
        if (isDemo || _user.value?.syncCompletionToClassroom == false) return
        completed.filter { it.source == Source.GoogleClassroom && it.externalId != null }.forEach { a ->
            scope.launch {
                runCatching { api.invoke("syncCompletionToClassroom", buildJsonObject { put("assignment_id", a.id) }) }
            }
        }
    }

    suspend fun deleteAssignment(a: Assignment): Boolean {
        val prev = _assignments.value
        _assignments.update { list -> list.filterNot { it.id == a.id } }
        if (isDemo) return true
        return try {
            api.delete(Entities.ASSIGNMENT, a.id)
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _assignments.value = prev
            messages.show("Couldn't delete the assignment", e.userMessage, error = true)
            false
        }
    }

    /** Bulk edit (priority / completed), like the dashboard's select mode. */
    suspend fun bulkUpdate(ids: Set<String>, patch: JsonObject) {
        if (ids.isEmpty()) return
        val prev = _assignments.value
        val priority = (patch["priority"] as? JsonPrimitive)?.contentOrNull?.let(Priority::from)
        val completed = (patch["completed"] as? JsonPrimitive)?.booleanOrNull
        _assignments.update { list ->
            list.map { a ->
                if (a.id !in ids) a else a.copy(
                    priority = priority ?: a.priority,
                    completed = completed ?: a.completed,
                )
            }
        }
        if (isDemo) return
        try {
            coroutineScope { ids.map { id -> async { api.updateAssignment(id, patch) } }.awaitAll() }
            if (completed == true) syncCompletionToClassroom(prev.filter { it.id in ids && !it.completed })
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _assignments.value = prev
            messages.show("Couldn't update the assignments", e.userMessage, error = true)
        }
    }

    /** Deletes several assignments (dashboard select mode). */
    suspend fun bulkDelete(ids: Set<String>) {
        if (ids.isEmpty()) return
        val prev = _assignments.value
        _assignments.update { list -> list.filterNot { it.id in ids } }
        if (isDemo) return
        val failed = coroutineScope {
            ids.map { id -> async { id.takeIf { runCatching { api.delete(Entities.ASSIGNMENT, id) }.isFailure } } }.awaitAll()
        }.filterNotNull().toSet()
        if (failed.isNotEmpty()) {
            _assignments.update { list -> list + prev.filter { it.id in failed } }
            messages.show("Couldn't delete ${failed.size} assignment(s)", error = true)
        }
    }

    /**
     * Moves several assignments to [day], keeping each one's time of day
     * (11:59 PM for ones without a due date).
     */
    suspend fun bulkReschedule(ids: Set<String>, day: java.time.LocalDate, zone: ZoneId = ZoneId.systemDefault()) {
        if (ids.isEmpty()) return
        val prev = _assignments.value
        val moved = prev.filter { it.id in ids }.associate { a ->
            a.id to (CalendarMath.moveToDay(a.dueDate, day, zone) ?: day.atTime(23, 59).atZone(zone).toInstant())
        }
        _assignments.update { list -> list.map { a -> moved[a.id]?.let { a.copy(dueDate = it) } ?: a } }
        if (isDemo) return
        try {
            coroutineScope {
                moved.map { (id, due) ->
                    async { api.updateAssignment(id, buildJsonObject { put("due_date", Dates.format(due)) }) }
                }.awaitAll()
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _assignments.value = prev
            messages.show("Couldn't reschedule the assignments", e.userMessage, error = true)
        }
    }

    /** Moves an assignment to another day, keeping its time of day (calendar drag and drop). */
    suspend fun reschedule(id: String, day: java.time.LocalDate, zone: ZoneId = ZoneId.systemDefault()) {
        val a = assignment(id) ?: return
        val newDate = CalendarMath.moveToDay(a.dueDate, day, zone) ?: return
        if (newDate == a.dueDate) return
        updateAssignment(id, buildJsonObject { put("due_date", Dates.format(newDate)) }, a.copy(dueDate = newDate))
    }

    private fun apply(updated: Assignment) {
        _assignments.update { list -> list.map { if (it.id == updated.id) updated else it } }
    }

    // endregion

    // region Classes

    suspend fun createClass(fields: JsonObject): SchoolClass? {
        if (isDemo) return null
        return try {
            api.create<SchoolClass>(Entities.CLASS, fields).also { c -> _classes.update { it + c } }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            messages.show("Couldn't add the class", e.userMessage, error = true)
            null
        }
    }

    suspend fun updateClass(id: String, patch: JsonObject): SchoolClass? {
        if (isDemo) return null
        return try {
            api.update<SchoolClass>(Entities.CLASS, id, patch).also { c ->
                _classes.update { list -> list.map { if (it.id == id) c else it } }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            messages.show("Couldn't save the class", e.userMessage, error = true)
            null
        }
    }

    suspend fun deleteClass(c: SchoolClass): Boolean {
        val prev = _classes.value
        _classes.update { list -> list.filterNot { it.id == c.id } }
        if (isDemo) return true
        return try {
            api.delete(Entities.CLASS, c.id)
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _classes.value = prev
            messages.show("Couldn't delete the class", e.userMessage, error = true)
            false
        }
    }

    // endregion

    // region Templates

    suspend fun createTemplate(fields: JsonObject): AssignmentTemplate? {
        if (isDemo) return null
        return try {
            api.create<AssignmentTemplate>(Entities.TEMPLATE, fields).also { t -> _templates.update { listOf(t) + it } }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            messages.show("Couldn't save the template", e.userMessage, error = true)
            null
        }
    }

    suspend fun updateTemplate(id: String, patch: JsonObject): AssignmentTemplate? {
        if (isDemo) return null
        return try {
            api.update<AssignmentTemplate>(Entities.TEMPLATE, id, patch).also { t ->
                _templates.update { list -> list.map { if (it.id == id) t else it } }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            messages.show("Couldn't save the template", e.userMessage, error = true)
            null
        }
    }

    suspend fun deleteTemplate(t: AssignmentTemplate) {
        val prev = _templates.value
        _templates.update { list -> list.filterNot { it.id == t.id } }
        if (isDemo) return
        try {
            api.delete(Entities.TEMPLATE, t.id)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _templates.value = prev
            messages.show("Couldn't delete the template", e.userMessage, error = true)
        }
    }

    // endregion

    // region Study sessions

    suspend fun createStudySession(fields: JsonObject): StudySession? {
        if (isDemo) return null
        return try {
            api.create<StudySession>(Entities.STUDY_SESSION, fields).also { s -> _studySessions.update { listOf(s) + it } }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            messages.show("Couldn't save the study session", e.userMessage, error = true)
            null
        }
    }

    // endregion

    // region Notifications

    suspend fun markRead(n: AppNotification) {
        if (n.read) return
        _notifications.update { list -> list.map { if (it.id == n.id) it.copy(read = true) else it } }
        if (isDemo) return
        runCatching { api.update<AppNotification>(Entities.NOTIFICATION, n.id, buildJsonObject { put("read", true) }) }
    }

    suspend fun markAllRead() {
        val unread = _notifications.value.filter { !it.read }
        _notifications.update { list -> list.map { it.copy(read = true) } }
        if (isDemo) return
        coroutineScope {
            unread.forEach { n ->
                launch { runCatching { api.update<AppNotification>(Entities.NOTIFICATION, n.id, buildJsonObject { put("read", true) }) } }
            }
        }
    }

    suspend fun deleteNotification(n: AppNotification) {
        _notifications.update { list -> list.filterNot { it.id == n.id } }
        if (isDemo) return
        runCatching { api.delete(Entities.NOTIFICATION, n.id) }
    }

    suspend fun clearNotifications() {
        val all = _notifications.value
        _notifications.value = emptyList()
        if (isDemo) return
        coroutineScope { all.forEach { n -> launch { runCatching { api.delete(Entities.NOTIFICATION, n.id) } } } }
    }

    // endregion
}
