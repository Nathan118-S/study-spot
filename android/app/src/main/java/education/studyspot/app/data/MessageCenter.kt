package education.studyspot.app.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A short message for the user (the iOS app's toasts). Shown as a snackbar by
 * the app shell, with a button labelled [actionLabel] that runs [onAction].
 */
data class UserMessage(
    val title: String,
    val detail: String? = null,
    val isError: Boolean = false,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/** App-wide toasts/snackbars. Anything can [show]; ui/MainScaffold displays them. */
class MessageCenter {
    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()

    fun show(title: String, detail: String? = null, error: Boolean = false) {
        _messages.tryEmit(UserMessage(title, detail, error))
    }

    /** A message with a button, e.g. "Use a calendar link instead". */
    fun show(title: String, detail: String?, error: Boolean, actionLabel: String, onAction: () -> Unit) {
        _messages.tryEmit(UserMessage(title, detail, error, actionLabel, onAction))
    }
}
