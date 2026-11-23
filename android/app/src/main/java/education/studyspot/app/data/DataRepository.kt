package education.studyspot.app.data

import androidx.annotation.VisibleForTesting
import education.studyspot.app.core.ApiClient
import education.studyspot.app.core.ApiException
import education.studyspot.app.core.AppJson
import education.studyspot.app.core.Dates
import education.studyspot.app.core.Entities
import education.studyspot.app.core.doubleValue
import education.studyspot.app.core.flexString
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
import education.studyspot.app.model.IcalImportResult
import education.studyspot.app.model.IcalSyncResult
import education.studyspot.app.model.Priority
import education.studyspot.app.model.SchoolClass
import education.studyspot.app.model.Source
import education.studyspot.app.model.StudySession
import education.studyspot.app.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
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
 *
 * Offline mode (with [storage]): the last loaded data is cached on disk and
 * shown at launch before the network answers. Writes that fail because the
 * device is offline (never a 4xx/5xx response) stay applied locally and are
 * queued ([OfflineQueue]); the queue replays in order when the connection
 * comes back ([onNetworkChanged]) and when the app returns to the foreground
 * ([syncPendingChanges]). Conflicts are last write wins (see [OfflineQueue]).
 */
class DataRepository(
    private val api: ApiClient,
    private val messages: MessageCenter,
    private val scope: CoroutineScope,
    /** Read cache and write queue on disk; null turns offline mode off (previews). */
    private val storage: OfflineStorage? = null,
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
    /**
     * Pasted calendar links: Google ones in Settings > Google, Blackboard ones in
     * Settings > Blackboard (see IcalFeed.feedKind). Loaded by [refreshIcalFeeds].
     */
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

    // Offline mode state (see the class comment and the "Offline" region).

    /** Single-threaded so cache/queue files are written in order. */
    private val disk = Dispatchers.IO.limitedParallelism(1)

    private val queue = OfflineQueue(persist = ::persistQueue)

    private val _isOffline = MutableStateFlow(false)
    /** The last request couldn't reach the server, or the device lost its network. */
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    /** Changes made offline that haven't reached the server yet. */
    val pendingChanges: StateFlow<Int> = queue.ops
        .map { it.size }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    private val _lastUpdated = MutableStateFlow<Instant?>(null)
    /** When the data on screen was last loaded from the server. */
    val lastUpdated: StateFlow<Instant?> = _lastUpdated.asStateFlow()

    private val _showingCachedData = MutableStateFlow(false)
    /** The data on screen came from the disk cache and hasn't been refreshed yet. */
    val showingCachedData: StateFlow<Boolean> = _showingCachedData.asStateFlow()

    /** The user whose cache and queue are loaded into memory. */
    private var restoredFor: String? = null
    private val replayLock = Mutex()

    init {
        if (storage != null) {
            // Keep the disk cache in step with what's on screen (including
            // offline edits), a moment after things settle.
            scope.launch {
                combine(_assignments, _classes, _notifications, _templates, _connections) { _, _, _, _, _ -> }
                    .collectLatest {
                        delay(500)
                        saveCache()
                    }
            }
        }
    }

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
        // Sign-out / account switch: forget the cache and any unsent changes.
        queue.restore(emptyList())
        restoredFor = null
        _isOffline.value = false
        _lastUpdated.value = null
        _showingCachedData.value = false
        storage?.let { s -> scope.launch(disk) { s.clear() } }
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
        // Show the cached copy at once, then send queued changes before re-fetching.
        _user.value?.let { restoreOfflineState(it) }
        if (!queue.isEmpty) syncPendingChanges(reload = false)
        if (!_hasLoaded.value) _isLoading.value = true
        try {
            fetchAssignmentsAndClasses()
            _hasLoaded.value = true
            _lastError.value = null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            noteFailure(e)
            // Offline with a cached copy on screen: the offline banner says enough.
            if (!(_hasLoaded.value && e.isOfflineError)) _lastError.value = e.userMessage
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
        try {
            fetchAssignmentsAndClasses()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            noteFailure(e)
        }
    }

    /** Both lists from the server, with still-queued offline changes laid on top. */
    private suspend fun fetchAssignmentsAndClasses() {
        coroutineScope {
            val a = async { api.assignments() }
            val c = async { api.classes() }
            _assignments.value = a.await()
            _classes.value = c.await()
        }
        overlayPending(Entities.ASSIGNMENT, Entities.CLASS)
        _isOffline.value = false
        _lastUpdated.value = Instant.now()
        _showingCachedData.value = false
    }

    /** Pull to refresh: syncs connected services first, like iOS. */
    suspend fun refresh() {
        _isRefreshing.value = true
        try {
            syncPendingChanges(reload = false)
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
        runCatching { api.notifications() }
            .onSuccess { _notifications.value = it; overlayPending(Entities.NOTIFICATION) }
            .onFailure(::noteFailure)
    }

    suspend fun loadTemplates() {
        if (isDemo) return
        runCatching { api.templates() }.onSuccess { _templates.value = it; overlayPending(Entities.TEMPLATE) }
    }

    suspend fun loadStudySessions() {
        if (isDemo) return
        runCatching { api.studySessions() }.onSuccess { _studySessions.value = it; overlayPending(Entities.STUDY_SESSION) }
    }

    // endregion

    // region Sync (Google Calendar / Classroom / Blackboard / calendar links)

    /** Auto-refresh synced data when the last sync was more than 6 hours ago. */
    suspend fun autoSyncIfStale() {
        val u = _user.value ?: return
        if (u.isDemo || !_connections.value.any || _isOffline.value) return
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

    /**
     * One-time import of a calendar file (a .ics, or Google Calendar's export
     * .zip) with functions/icalImportFile. Re-importing never duplicates. Throws
     * the server's user-ready message (not a calendar, too large, demo).
     */
    suspend fun importIcalFile(filename: String, bytes: ByteArray, mode: String): IcalImportResult {
        val result = api.invokeAs<IcalImportResult>("icalImportFile", buildJsonObject {
            put("filename", filename)
            put("data", java.util.Base64.getEncoder().encodeToString(bytes))
            put("mode", mode)
        })
        reloadData()
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

    /**
     * POST /api/entities/Assignment. [fields] uses server column names (title, class_id, due_date, …).
     * Offline, the assignment is added locally with a temporary id and queued.
     */
    suspend fun createAssignment(fields: JsonObject): Assignment? {
        if (isDemo) return null
        val op = PendingOp(kind = PendingOp.Kind.Create, entity = Entities.ASSIGNMENT, id = OfflineQueue.newTempId(), body = fields)
        return try {
            when (val r = sendOrQueue(op) { api.create<Assignment>(Entities.ASSIGNMENT, fields) }) {
                is Write.Sent -> r.value.also { created -> _assignments.update { listOf(created) + it } }
                Write.Queued -> assignment(op.id)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            messages.show("Couldn't add the assignment", e.userMessage, error = true)
            null
        }
    }

    /**
     * PATCH an assignment. [optimistic] (if given) is shown immediately and
     * rolled back on failure. Returns the server's copy (the local copy when
     * queued offline), or null on failure.
     */
    suspend fun updateAssignment(id: String, patch: JsonObject, optimistic: Assignment? = null): Assignment? {
        val prev = _assignments.value
        optimistic?.let(::apply)
        if (isDemo) return optimistic
        val op = PendingOp(kind = PendingOp.Kind.Update, entity = Entities.ASSIGNMENT, id = id, body = patch)
        return try {
            when (val r = sendOrQueue(op) { api.updateAssignment(id, patch) }) {
                is Write.Sent -> r.value.also(::apply)
                Write.Queued -> assignment(id)
            }
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
        // Offline: the completion itself is queued; Classroom isn't told (it's a function call, not a row write).
        if (isDemo || _isOffline.value || _user.value?.syncCompletionToClassroom == false) return
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
            sendOrQueue(PendingOp(kind = PendingOp.Kind.Delete, entity = Entities.ASSIGNMENT, id = a.id)) {
                api.delete(Entities.ASSIGNMENT, a.id)
            }
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
            val results = coroutineScope {
                ids.map { id ->
                    async {
                        sendOrQueue(PendingOp(kind = PendingOp.Kind.Update, entity = Entities.ASSIGNMENT, id = id, body = patch)) {
                            api.updateAssignment(id, patch)
                        }
                    }
                }.awaitAll()
            }
            if (completed == true && results.all { it is Write.Sent }) {
                syncCompletionToClassroom(prev.filter { it.id in ids && !it.completed })
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _assignments.value = prev
            overlayPending(Entities.ASSIGNMENT) // keep whatever did get queued
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
            ids.map { id ->
                async {
                    id.takeIf {
                        runCatching {
                            sendOrQueue(PendingOp(kind = PendingOp.Kind.Delete, entity = Entities.ASSIGNMENT, id = id)) {
                                api.delete(Entities.ASSIGNMENT, id)
                            }
                        }.isFailure
                    }
                }
            }.awaitAll()
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
                    async {
                        val patch = buildJsonObject { put("due_date", Dates.format(due)) }
                        sendOrQueue(PendingOp(kind = PendingOp.Kind.Update, entity = Entities.ASSIGNMENT, id = id, body = patch)) {
                            api.updateAssignment(id, patch)
                        }
                    }
                }.awaitAll()
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _assignments.value = prev
            overlayPending(Entities.ASSIGNMENT)
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
        val op = PendingOp(kind = PendingOp.Kind.Create, entity = Entities.CLASS, id = OfflineQueue.newTempId(), body = fields)
        return try {
            when (val r = sendOrQueue(op) { api.create<SchoolClass>(Entities.CLASS, fields) }) {
                is Write.Sent -> r.value.also { c -> _classes.update { it + c } }
                Write.Queued -> classFor(op.id)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            messages.show("Couldn't add the class", e.userMessage, error = true)
            null
        }
    }

    suspend fun updateClass(id: String, patch: JsonObject): SchoolClass? {
        if (isDemo) return null
        val op = PendingOp(kind = PendingOp.Kind.Update, entity = Entities.CLASS, id = id, body = patch)
        return try {
            when (val r = sendOrQueue(op) { api.update<SchoolClass>(Entities.CLASS, id, patch) }) {
                is Write.Sent -> r.value.also { c -> _classes.update { list -> list.map { if (it.id == id) c else it } } }
                Write.Queued -> classFor(id)
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
            sendOrQueue(PendingOp(kind = PendingOp.Kind.Delete, entity = Entities.CLASS, id = c.id)) { api.delete(Entities.CLASS, c.id) }
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
        val op = PendingOp(kind = PendingOp.Kind.Create, entity = Entities.TEMPLATE, id = OfflineQueue.newTempId(), body = fields)
        return try {
            when (val r = sendOrQueue(op) { api.create<AssignmentTemplate>(Entities.TEMPLATE, fields) }) {
                is Write.Sent -> r.value.also { t -> _templates.update { listOf(t) + it } }
                Write.Queued -> _templates.value.firstOrNull { it.id == op.id }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            messages.show("Couldn't save the template", e.userMessage, error = true)
            null
        }
    }

    suspend fun updateTemplate(id: String, patch: JsonObject): AssignmentTemplate? {
        if (isDemo) return null
        val op = PendingOp(kind = PendingOp.Kind.Update, entity = Entities.TEMPLATE, id = id, body = patch)
        return try {
            when (val r = sendOrQueue(op) { api.update<AssignmentTemplate>(Entities.TEMPLATE, id, patch) }) {
                is Write.Sent -> r.value.also { t -> _templates.update { list -> list.map { if (it.id == id) t else it } } }
                Write.Queued -> _templates.value.firstOrNull { it.id == id }
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
            sendOrQueue(PendingOp(kind = PendingOp.Kind.Delete, entity = Entities.TEMPLATE, id = t.id)) { api.delete(Entities.TEMPLATE, t.id) }
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
        val op = PendingOp(kind = PendingOp.Kind.Create, entity = Entities.STUDY_SESSION, id = OfflineQueue.newTempId(), body = fields)
        return try {
            when (val r = sendOrQueue(op) { api.create<StudySession>(Entities.STUDY_SESSION, fields) }) {
                is Write.Sent -> r.value.also { s -> _studySessions.update { listOf(s) + it } }
                Write.Queued -> _studySessions.value.firstOrNull { it.id == op.id }
            }
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
        runCatching { sendReadFlag(n.id) }
    }

    suspend fun markAllRead() {
        val unread = _notifications.value.filter { !it.read }
        _notifications.update { list -> list.map { it.copy(read = true) } }
        if (isDemo) return
        coroutineScope {
            unread.forEach { n -> launch { runCatching { sendReadFlag(n.id) } } }
        }
    }

    private suspend fun sendReadFlag(id: String) {
        val patch = buildJsonObject { put("read", true) }
        sendOrQueue(PendingOp(kind = PendingOp.Kind.Update, entity = Entities.NOTIFICATION, id = id, body = patch)) {
            api.update<AppNotification>(Entities.NOTIFICATION, id, patch)
        }
    }

    suspend fun deleteNotification(n: AppNotification) {
        _notifications.update { list -> list.filterNot { it.id == n.id } }
        if (isDemo) return
        runCatching { sendNotificationDelete(n.id) }
    }

    suspend fun clearNotifications() {
        val all = _notifications.value
        _notifications.value = emptyList()
        if (isDemo) return
        coroutineScope { all.forEach { n -> launch { runCatching { sendNotificationDelete(n.id) } } } }
    }

    private suspend fun sendNotificationDelete(id: String) {
        sendOrQueue(PendingOp(kind = PendingOp.Kind.Delete, entity = Entities.NOTIFICATION, id = id)) {
            api.delete(Entities.NOTIFICATION, id)
        }
    }

    // endregion

    // region Offline (read cache + write queue)

    private sealed interface Write<out T> {
        data class Sent<T>(val value: T) : Write<T>
        data object Queued : Write<Nothing>
    }

    /**
     * Sends a write, or queues [op] (already applied locally) when the device is
     * offline. While earlier changes are still queued, new ones queue behind
     * them so they reach the server in the order they were made. Any other
     * failure (a 4xx/5xx response) is thrown, for the caller to roll back.
     */
    private suspend fun <T> sendOrQueue(op: PendingOp, send: suspend () -> T): Write<T> {
        if (storage != null && (_isOffline.value || !queue.isEmpty || OfflineQueue.isTempId(op.id))) {
            enqueue(op)
            if (!_isOffline.value) scope.launch { syncPendingChanges() }
            return Write.Queued
        }
        return try {
            Write.Sent(send()).also { _isOffline.value = false }
        } catch (e: ApiException) {
            if (storage == null || !e.isOffline) throw e
            _isOffline.value = true
            enqueue(op)
            Write.Queued
        }
    }

    private fun enqueue(op: PendingOp) {
        applyLocally(op)
        queue.enqueue(op)
    }

    /**
     * Replays queued offline changes in order (connection back, app in the
     * foreground, pull to refresh), then reloads. A change the server refuses
     * (4xx) is dropped with a message; offline or 5xx keeps it for next time.
     */
    suspend fun syncPendingChanges(reload: Boolean = true) {
        if (isDemo || storage == null || queue.isEmpty) return
        if (!replayLock.tryLock()) return
        val report = try {
            queue.replay(::sendQueued)
        } finally {
            replayLock.unlock()
        }
        if (report.idMap.isNotEmpty()) remapLocalIds(report.idMap)
        if (report.rejected.isNotEmpty()) {
            val title = if (report.rejected.size == 1) "A change couldn't be synced" else "${report.rejected.size} changes couldn't be synced"
            messages.show(title, report.rejected.first(), error = true)
        }
        if (reload && (report.sent > 0 || report.rejected.isNotEmpty())) {
            coroutineScope {
                launch { reloadData() }
                launch { loadNotifications() }
                launch { loadTemplates() }
            }
        }
    }

    private suspend fun sendQueued(op: PendingOp): SendResult = try {
        val path = "/entities/${op.entity}"
        val result = when (op.kind) {
            PendingOp.Kind.Create -> {
                val res = api.send(path, "POST", op.body ?: JsonObject(emptyMap()))
                SendResult.Done(createdId = res["id"].flexString())
            }
            PendingOp.Kind.Update -> {
                api.send("$path/${op.id}", "PATCH", op.body ?: JsonObject(emptyMap()))
                SendResult.Done()
            }
            PendingOp.Kind.Delete -> {
                api.delete(op.entity, op.id)
                SendResult.Done()
            }
        }
        _isOffline.value = false
        result
    } catch (e: ApiException) {
        when {
            e.isOffline -> {
                _isOffline.value = true
                SendResult.RetryLater
            }
            // Already gone on the server: that's what the delete wanted.
            op.kind == PendingOp.Kind.Delete && e.status == 404 -> SendResult.Done()
            // 401 signs out (AuthStore); 408/429 are worth another try.
            e.status in 400..499 && e.status !in setOf(401, 408, 429) -> SendResult.Rejected(e.userMessage)
            else -> SendResult.RetryLater
        }
    }

    /** Connectivity changed (core/ConnectivityMonitor). */
    fun onNetworkChanged(online: Boolean) {
        if (!online) {
            _isOffline.value = true
            return
        }
        val wasOffline = _isOffline.value
        _isOffline.value = false
        if (_user.value == null || isDemo || storage == null) return
        scope.launch {
            syncPendingChanges()
            if (wasOffline && (_showingCachedData.value || _hasLoaded.value)) {
                refreshUser()
                reloadData()
                loadNotifications()
            }
        }
    }

    /** The user from the disk cache, for starting signed in while offline (AuthStore.check). */
    suspend fun cachedUser(): User? {
        val s = storage ?: return null
        return withContext(disk) { s.readCache() }?.user?.takeUnless { it.isDemo }
    }

    /** Starting from the cache because the server can't be reached. */
    fun markOffline() {
        _isOffline.value = true
    }

    private fun noteFailure(e: Throwable) {
        if (e.isOfflineError) _isOffline.value = true
    }

    private val Throwable.isOfflineError: Boolean get() = (this as? ApiException)?.isOffline == true

    /** Loads the cache (if nothing is on screen yet) and the queue for [u]. */
    private suspend fun restoreOfflineState(u: User) {
        val s = storage ?: return
        if (u.isDemo || restoredFor == u.id) return
        restoredFor = u.id
        val (cache, ops) = withContext(disk) {
            val c = s.readCache()
            if (c != null && c.userId != u.id) {
                // Someone else's data: drop it.
                s.clear()
                null to emptyList()
            } else {
                c to s.readQueue(u.id)
            }
        }
        queue.restore(ops)
        if (cache != null && !_hasLoaded.value) {
            _assignments.value = cache.assignments
            _classes.value = cache.classes
            _notifications.value = cache.notifications
            _templates.value = cache.templates
            _connections.value = cache.connections
            _lastUpdated.value = Instant.ofEpochMilli(cache.savedAt)
            _showingCachedData.value = true
            _hasLoaded.value = true
        }
        overlayPending()
    }

    private suspend fun saveCache() {
        val s = storage ?: return
        val u = _user.value ?: return
        if (u.isDemo || !_hasLoaded.value || restoredFor != u.id) return
        val updated = _lastUpdated.value ?: return
        val cache = OfflineCache(
            userId = u.id,
            savedAt = updated.toEpochMilli(),
            user = u,
            assignments = _assignments.value,
            classes = _classes.value,
            notifications = _notifications.value,
            templates = _templates.value,
            connections = _connections.value,
        )
        withContext(disk) { s.writeCache(cache) }
    }

    private fun persistQueue(ops: List<PendingOp>) {
        val s = storage ?: return
        val userId = _user.value?.id ?: return
        scope.launch(disk) { s.writeQueue(userId, ops) }
    }

    /** Lays the still-queued ops over the in-memory lists (all entities when none given). */
    private fun overlayPending(vararg entities: String) {
        queue.ops.value
            .filter { entities.isEmpty() || it.entity in entities }
            .forEach(::applyLocally)
    }

    private fun applyLocally(op: PendingOp) {
        when (op.entity) {
            Entities.ASSIGNMENT -> _assignments.update { applyOp(it, op, Assignment.serializer()) { a -> a.id } }
            Entities.CLASS -> _classes.update { applyOp(it, op, SchoolClass.serializer(), prepend = false) { c -> c.id } }
            Entities.NOTIFICATION -> _notifications.update { applyOp(it, op, AppNotification.serializer()) { n -> n.id } }
            Entities.TEMPLATE -> _templates.update { applyOp(it, op, AssignmentTemplate.serializer()) { t -> t.id } }
            Entities.STUDY_SESSION -> _studySessions.update { applyOp(it, op, StudySession.serializer()) { s -> s.id } }
        }
    }

    private fun <T> applyOp(list: List<T>, op: PendingOp, serializer: KSerializer<T>, prepend: Boolean = true, idOf: (T) -> String): List<T> {
        // A row created offline gets a creation time so it sorts like a new one.
        val withDate = if (op.kind == PendingOp.Kind.Create && op.body?.containsKey("created_date") != true) {
            op.copy(body = JsonObject((op.body ?: JsonObject(emptyMap())) + ("created_date" to JsonPrimitive(Dates.format(Instant.now())))))
        } else {
            op
        }
        return applyPendingOp(list, withDate, serializer, AppJson, idOf, prepend)
    }

    /** After queued creates reach the server: swap temporary ids for the real ones on screen. */
    private fun remapLocalIds(idMap: Map<String, String>) {
        fun real(id: String?) = id?.let { idMap[it] ?: it }
        _assignments.update { list -> list.map { a -> a.copy(id = real(a.id)!!, classId = real(a.classId)) } }
        _classes.update { list -> list.map { c -> c.copy(id = real(c.id)!!) } }
        _templates.update { list -> list.map { t -> t.copy(id = real(t.id)!!) } }
    }

    // endregion
}
