package com.cloudflared.launcher.termux

import com.cloudflared.launcher.model.TunnelProfile

object ShellScripts {
    const val TERMUX_HOME = "/data/data/com.termux/files/home"
    const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
    const val BASH_PATH = "$TERMUX_PREFIX/bin/bash"
    private const val ROOT_DIR = "$TERMUX_HOME/.cloudflared-launcher"

    fun safeProfileName(profile: TunnelProfile): String = "profile-${profile.id.take(8)}"

    fun profileDir(profile: TunnelProfile): String =
        "$ROOT_DIR/profiles/${safeProfileName(profile)}"

    fun scriptPath(profile: TunnelProfile, scriptName: String): String =
        "${profileDir(profile)}/$scriptName"

    fun installCommand(profile: TunnelProfile): String {
        val dir = shellSingle(profileDir(profile))
        val nameLine = shellSingle("CF_TUNNEL_NAME=\"${envDouble(profile.name)}\"")
        val tokenLine = shellSingle("CF_TUNNEL_TOKEN=\"${envDouble(profile.token)}\"")
        val installedMessage = shellSingle("installed: ${profile.name}")

        return """
            set -e
            DIR=$dir
            mkdir -p "${'$'}DIR"
            printf '%s\n' $nameLine $tokenLine > "${'$'}DIR/config.env"

            cat > "${'$'}DIR/start.sh" <<'EOF'
            #!/data/data/com.termux/files/usr/bin/bash
            cd "${'$'}(dirname "${'$'}0")" || exit 1
            if [ -f ./config.env ]; then
              . ./config.env
            fi
            if [ -z "${'$'}{CF_TUNNEL_TOKEN:-}" ]; then
              echo "missing tunnel token"
              exit 1
            fi
            if [ -f ./cloudflared.pid ]; then
              PID="${'$'}(cat ./cloudflared.pid 2>/dev/null || true)"
              if [ -n "${'$'}PID" ] && kill -0 "${'$'}PID" 2>/dev/null; then
                echo "already running: ${'$'}{CF_TUNNEL_NAME:-cloudflared}"
                exit 0
              fi
            fi
            nohup cloudflared tunnel run --token "${'$'}CF_TUNNEL_TOKEN" > ./cloudflared.log 2>&1 &
            echo ${'$'}! > ./cloudflared.pid
            echo "started: ${'$'}{CF_TUNNEL_NAME:-cloudflared}"
            EOF

            cat > "${'$'}DIR/stop.sh" <<'EOF'
            #!/data/data/com.termux/files/usr/bin/bash
            cd "${'$'}(dirname "${'$'}0")" || exit 1
            if [ -f ./cloudflared.pid ]; then
              PID="${'$'}(cat ./cloudflared.pid 2>/dev/null || true)"
              if [ -n "${'$'}PID" ] && kill -0 "${'$'}PID" 2>/dev/null; then
                kill "${'$'}PID" 2>/dev/null || true
              fi
              rm -f ./cloudflared.pid
            fi
            echo "stopped"
            EOF

            cat > "${'$'}DIR/status.sh" <<'EOF'
            #!/data/data/com.termux/files/usr/bin/bash
            cd "${'$'}(dirname "${'$'}0")" || exit 1
            if [ -f ./cloudflared.pid ]; then
              PID="${'$'}(cat ./cloudflared.pid 2>/dev/null || true)"
              if [ -n "${'$'}PID" ] && kill -0 "${'$'}PID" 2>/dev/null; then
                echo "running"
                exit 0
              fi
            fi
            echo "stopped"
            EOF

            cat > "${'$'}DIR/log.sh" <<'EOF'
            #!/data/data/com.termux/files/usr/bin/bash
            cd "${'$'}(dirname "${'$'}0")" || exit 1
            if [ -f ./cloudflared.log ]; then
              tail -n 120 ./cloudflared.log
            else
              echo "no log file"
            fi
            EOF

            cat > "${'$'}DIR/clear-log.sh" <<'EOF'
            #!/data/data/com.termux/files/usr/bin/bash
            cd "${'$'}(dirname "${'$'}0")" || exit 1
            : > ./cloudflared.log
            echo "log cleared"
            EOF

            touch "${'$'}DIR/cloudflared.log" "${'$'}DIR/cloudflared.pid"
            chmod 700 "${'$'}DIR"/*.sh
            if ! command -v cloudflared >/dev/null 2>&1; then
              echo "warning: cloudflared is not installed. Run: pkg install cloudflared"
            fi
            printf '%s\n' $installedMessage
        """.trimIndent()
    }

    private fun envDouble(value: String): String = value
        .replace("\\", "\\\\")
        .replace("$", "\\$")
        .replace("\"", "\\\"")
        .replace("`", "\\`")
        .replace("\n", " ")
        .replace("\r", " ")

    private fun shellSingle(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"
}
