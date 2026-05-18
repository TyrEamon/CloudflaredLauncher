package com.cloudflared.launcher.termux

import kotlinx.coroutines.flow.MutableSharedFlow

data class CommandResultEvent(
    val profileId: String,
    val action: TermuxAction,
    val output: String
)

object CommandResultBus {
    val events = MutableSharedFlow<CommandResultEvent>(extraBufferCapacity = 32)
}
