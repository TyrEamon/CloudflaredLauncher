# Cloudflared Launcher

Cloudflared Launcher is a native Android app built with Kotlin and Jetpack Compose.

It does not log in to Cloudflare, create tunnels in the Cloudflare dashboard, call the Cloudflare API, upload tokens, or request network access. It only stores tunnel profiles locally and uses the Termux `RUN_COMMAND` intent to run local `cloudflared` scripts.

## Build APK with GitHub Actions

1. Create a GitHub repository from this folder.
2. Push the project.
3. Open the `Build Android APK` workflow.
4. Download the `cloudflared-launcher-debug-apk` artifact.

## Termux preparation

Install Termux, open it once, then run:

```sh
pkg update
pkg install cloudflared
mkdir -p ~/.termux
echo "allow-external-apps = true" >> ~/.termux/termux.properties
```

Restart Termux after changing `termux.properties`.

In Cloudflare Zero Trust, create the Tunnel and configure the Public Hostname service URL, for example:

```text
http://127.0.0.1:5244
```

Then copy the Cloudflare tunnel token into this app.
