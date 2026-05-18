package com.cloudflared.launcher.model

data class TunnelProfile(
    val id: String,
    val name: String,
    val token: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long
)

enum class TunnelStatus(val label: String) {
    NotInstalled("未安装"),
    Stopped("已停止"),
    Running("运行中"),
    Unknown("未知")
}
