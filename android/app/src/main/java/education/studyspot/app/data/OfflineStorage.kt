package education.studyspot.app.data

import education.studyspot.app.core.AppJson
import education.studyspot.app.model.AppNotification
import education.studyspot.app.model.Assignment
import education.studyspot.app.model.AssignmentTemplate
import education.studyspot.app.model.Connections
import education.studyspot.app.model.SchoolClass
import education.studyspot.app.model.User
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.io.File

/** The last data loaded for one user, shown at launch before the network answers. */
@Serializable
data class OfflineCache(
    val userId: String,
    /** When the data was last loaded from the server (epoch millis). */
    val savedAt: Long,
    val user: User? = null,
    val assignments: List<Assignment> = emptyList(),
    val classes: List<SchoolClass> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val templates: List<AssignmentTemplate> = emptyList(),
    val connections: Connections = Connections(),
)

@Serializable
data class OfflineQueueFile(val userId: String, val ops: List<PendingOp> = emptyList())

/**
 * The read cache and the write queue on disk (`files/offline/`). Holds one
 * user at a time: everything is wiped on sign-out, and data for a different
 * user is ignored (and wiped) on load. Plain files, no Android APIs, so it can
 * be used from JVM tests with a temp directory.
 */
class OfflineStorage(private val dir: File) {
    private val cacheFile get() = File(dir, "cache.json")
    private val queueFile get() = File(dir, "queue.json")

    fun readCache(userId: String? = null): OfflineCache? =
        read(cacheFile, OfflineCache.serializer())?.takeIf { userId == null || it.userId == userId }

    fun writeCache(cache: OfflineCache) = write(cacheFile, OfflineCache.serializer(), cache)

    fun readQueue(userId: String): List<PendingOp> =
        read(queueFile, OfflineQueueFile.serializer())?.takeIf { it.userId == userId }?.ops.orEmpty()

    fun writeQueue(userId: String, ops: List<PendingOp>) {
        if (ops.isEmpty()) queueFile.delete() else write(queueFile, OfflineQueueFile.serializer(), OfflineQueueFile(userId, ops))
    }

    /** Sign-out / account switch. */
    fun clear() {
        dir.deleteRecursively()
    }

    private fun <T> read(file: File, serializer: KSerializer<T>): T? = runCatching {
        if (!file.exists()) null else AppJson.decodeFromString(serializer, file.readText())
    }.getOrNull()

    @Synchronized
    private fun <T> write(file: File, serializer: KSerializer<T>, value: T) {
        runCatching {
            dir.mkdirs()
            // Write then rename, so a crash mid-write never leaves a half file.
            val tmp = File(dir, file.name + ".tmp")
            tmp.writeText(AppJson.encodeToString(serializer, value))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}
