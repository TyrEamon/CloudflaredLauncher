package com.cloudflared.launcher.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import com.cloudflared.launcher.model.TunnelProfile

data class DispatchResult(
    val success: Boolean,
    val message: String = ""
)

class TermuxCommandRunner(private val context: Context) {
    fun install(profile: TunnelProfile): DispatchResult =
        dispatch(
            profile = profile,
            action = TermuxAction.Install,
            commandPath = ShellScripts.BASH_PATH,
            arguments = arrayOf("-lc", ShellScripts.installCommand(profile)),
            workdir = ShellScripts.TERMUX_HOME,
            description = "Create cloudflared launcher scripts"
        )

    fun start(profile: TunnelProfile): DispatchResult =
        runScript(profile, TermuxAction.Start, "start.sh")

    fun stop(profile: TunnelProfile): DispatchResult =
        runScript(profile, TermuxAction.Stop, "stop.sh")

    fun status(profile: TunnelProfile): DispatchResult =
        runScript(profile, TermuxAction.Status, "status.sh")

    fun log(profile: TunnelProfile): DispatchResult =
        runScript(profile, TermuxAction.Log, "log.sh")

    fun clearLog(profile: TunnelProfile): DispatchResult =
        runScript(profile, TermuxAction.ClearLog, "clear-log.sh")

    private fun runScript(
        profile: TunnelProfile,
        action: TermuxAction,
        scriptName: String
    ): DispatchResult = dispatch(
        profile = profile,
        action = action,
        commandPath = ShellScripts.scriptPath(profile, scriptName),
        arguments = emptyArray(),
        workdir = ShellScripts.profileDir(profile),
        description = action.label
    )

    private fun dispatch(
        profile: TunnelProfile,
        action: TermuxAction,
        commandPath: String,
        arguments: Array<String>,
        workdir: String,
        description: String
    ): DispatchResult {
        if (context.checkSelfPermission(TermuxConstants.PERMISSION_RUN_COMMAND) != PackageManager.PERMISSION_GRANTED) {
            return DispatchResult(
                success = false,
                message = "缺少 Termux RUN_COMMAND 权限。请到 Android 设置 > 应用 > Cloudflared Launcher > 权限 > 其他权限或 Additional permissions > 允许 Run commands in Termux environment。"
            )
        }

        val requestCode = profile.id.hashCode() xor action.ordinal xor SystemClock.uptimeMillis().toInt()
        val resultIntent = Intent(context, TermuxResultService::class.java)
            .putExtra(TermuxResultService.EXTRA_PROFILE_ID, profile.id)
            .putExtra(TermuxResultService.EXTRA_ACTION, action.name)

        val pendingFlags = PendingIntent.FLAG_ONE_SHOT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }

        val pendingIntent = PendingIntent.getService(
            context,
            requestCode,
            resultIntent,
            pendingFlags
        )

        val intent = Intent(TermuxConstants.ACTION_RUN_COMMAND)
            .setClassName(TermuxConstants.PACKAGE_NAME, TermuxConstants.RUN_COMMAND_SERVICE_NAME)
            .putExtra(TermuxConstants.EXTRA_COMMAND_PATH, commandPath)
            .putExtra(TermuxConstants.EXTRA_ARGUMENTS, arguments)
            .putExtra(TermuxConstants.EXTRA_WORKDIR, workdir)
            .putExtra(TermuxConstants.EXTRA_BACKGROUND, true)
            .putExtra(TermuxConstants.EXTRA_PENDING_INTENT, pendingIntent)
            .putExtra(TermuxConstants.EXTRA_COMMAND_LABEL, "Cloudflared Launcher")
            .putExtra(TermuxConstants.EXTRA_COMMAND_DESCRIPTION, description)

        return runCatching {
            context.startService(intent)
            DispatchResult(success = true)
        }.getOrElse { error ->
            DispatchResult(success = false, message = error.message.orEmpty())
        }
    }

    companion object {
        val failureHint = """
            执行失败，请检查：
            - 请确认已安装 Termux；
            - 请确认已开启 allow-external-apps；
            - 请确认已授予本 App Termux RUN_COMMAND 权限；
            - 请先手动打开一次 Termux 再重试。
        """.trimIndent()
    }
}
