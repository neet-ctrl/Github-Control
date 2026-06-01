package com.githubcontrol.notifications

import kotlinx.coroutines.flow.MutableSharedFlow

object DeepLinkBus {
    val pendingRoute = MutableSharedFlow<String>(extraBufferCapacity = 4)
}
