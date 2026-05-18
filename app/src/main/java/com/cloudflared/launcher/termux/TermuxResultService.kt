package com.cloudflared.launcher.termux

import android.app.Service
import android.app.Activity
import android.content.Intent
import android.os.IBinder
import com.cloudflared.launcher.data.TunnelRepository
import com.cloudflared.launcher.model.TunnelStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TermuxResultService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            handleResult(intent)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun handleResult(intent: Intent) {
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID) ?: return
        val action = runCatching {
            TermuxAction.valueOf(intent.getStringExtra(EXTRA_ACTION).orEmpty())
        }.getOrDefault(TermuxAction.Status)

        val result = intent.getBundleExtra(TermuxConstants.RESULT_BUNDLE)
        val stdout = result?.getString(TermuxConstants.RESULT_STDOUT).orEmpty()
        val stderr = result?.getString(TermuxConstants.RESULT_STDERR).orEmpty()
        val errMessage = result?.getString(TermuxConstants.RESULT_ERRMSG).orEmpty()
        val exitCode = if (result?.containsKey(TermuxConstants.RESULT_EXIT_CODE) == true) {
            result.getInt(TermuxConstants.RESULT_EXIT_CODE)
        } else {
            null
        }
        val termuxErr = if (result?.containsKey(TermuxConstants.RESULT_ERR) == true) {
            result.getInt(TermuxConstants.RESULT_ERR)
        } else {
            0
        }
        val success = (termuxErr == 0 || termuxErr == Activity.RESULT_OK) &&
            (exitCode == null || exitCode == 0)
        val output = buildString {
            append("[")
            append(timestamp())
            append("] ")
            append(action.label)
            append('\n')
            if (stdout.isNotBlank()) append(stdout.trimEnd()).append('\n')
            if (stderr.isNotBlank()) append(stderr.trimEnd()).append('\n')
            if (errMessage.isNotBlank()) append(errMessage.trimEnd()).append('\n')
            if (!success) {
                append("exitCode=")
                append(exitCode ?: "unknown")
                append(", termuxErr=")
                append(termuxErr)
                append('\n')
                append(TermuxCommandRunner.failureHint)
                append('\n')
            }
            append('\n')
        }

        val repository = TunnelRepository(applicationContext)
        if (action == TermuxAction.ClearLog) {
            repository.replaceLog(profileId, output)
        } else {
            repository.appendLog(profileId, output)
        }

        if (success) {
            updateState(repository, profileId, action, stdout)
        } else {
            repository.setStatus(profileId, TunnelStatus.Unknown)
        }

        CommandResultBus.events.tryEmit(CommandResultEvent(profileId, action, output))
    }

    private fun updateState(
        repository: TunnelRepository,
        profileId: String,
        action: TermuxAction,
        stdout: String
    ) {
        when (action) {
            TermuxAction.Install -> {
                repository.markInstalled(profileId, true)
                repository.setStatus(profileId, TunnelStatus.Stopped)
            }
            TermuxAction.Start -> repository.setStatus(profileId, TunnelStatus.Running)
            TermuxAction.Stop -> repository.setStatus(profileId, TunnelStatus.Stopped)
            TermuxAction.Status -> {
                val normalized = stdout.trim().lowercase()
                val status = if (normalized.endsWith("running")) {
                    TunnelStatus.Running
                } else if (normalized.endsWith("stopped")) {
                    TunnelStatus.Stopped
                } else {
                    TunnelStatus.Unknown
                }
                repository.setStatus(profileId, status)
            }
            TermuxAction.Log -> Unit
            TermuxAction.ClearLog -> Unit
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_ACTION = "action"
    }
}
