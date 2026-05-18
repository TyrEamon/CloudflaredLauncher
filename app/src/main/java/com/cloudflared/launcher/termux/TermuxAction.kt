package com.cloudflared.launcher.termux

enum class TermuxAction(val label: String) {
    Install("安装到 Termux"),
    Start("启动 Tunnel"),
    Stop("停止 Tunnel"),
    Status("查看状态"),
    Log("查看日志"),
    ClearLog("清空日志")
}
