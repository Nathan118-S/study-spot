package education.studyspot.app.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** A short message for the user (the iOS app's toasts). Shown as a snackbar by the app shell. */
data class UserMessage(val title: String, val detail: String? = null, val isError: Boolean = false)

/** App-wide toasts/snackbars. Anything can [show]; ui/MainScaffold displays them. */
class MessageCenter {
    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()

    fun show(title: String, detail: String? = null, error: Boolean = false) {
        _messages.tryEmit(UserMessage(title, detail, error))
    }
}
