package com.borntocraft.typewriter.community.common

import com.typewritermc.core.extension.annotations.Help

data class WebhookSettings(
    @Help("Enable delivery to this webhook")
    val enabled: Boolean = false,
    @Help("Full Discord webhook URL")
    val url: String = "",
    @Help("Override webhook username (optional)")
    val username: String? = null,
    @Help("Override webhook avatar URL (optional)")
    val avatarUrl: String? = null,
)
