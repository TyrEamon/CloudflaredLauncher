package com.cloudflared.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cloudflared.launcher.data.TunnelRepository
import com.cloudflared.launcher.model.TunnelProfile
import com.cloudflared.launcher.model.TunnelStatus
import com.cloudflared.launcher.termux.CommandResultBus
import com.cloudflared.launcher.termux.TermuxAction
import com.cloudflared.launcher.termux.TermuxCommandRunner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudflaredLauncherApp(
    repository: TunnelRepository,
    runner: TermuxCommandRunner
) {
    var profiles by remember { mutableStateOf(repository.getProfiles()) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var screen by remember {
        mutableStateOf<Screen>(
            if (repository.hasSeenOnboarding()) Screen.Home else Screen.Onboarding
        )
    }

    fun reload() {
        profiles = repository.getProfiles()
        refreshTick += 1
    }

    fun appendLocalLog(profileId: String, message: String) {
        repository.appendLog(profileId, "[${timestamp()}] $message\n\n")
        reload()
    }

    fun runAction(profile: TunnelProfile, action: TermuxAction) {
        if (action != TermuxAction.Install && !repository.isInstalled(profile.id)) {
            appendLocalLog(profile.id, "请先在详情页执行“安装到 Termux”。")
            screen = Screen.Detail(profile.id)
            return
        }

        if (action == TermuxAction.ClearLog) {
            repository.clearLog(profile.id)
        }
        repository.markUsed(profile.id)
        appendLocalLog(profile.id, "请求：${action.label}")

        val result = when (action) {
            TermuxAction.Install -> runner.install(profile)
            TermuxAction.Start -> runner.start(profile)
            TermuxAction.Stop -> runner.stop(profile)
            TermuxAction.Status -> runner.status(profile)
            TermuxAction.Log -> runner.log(profile)
            TermuxAction.ClearLog -> runner.clearLog(profile)
        }

        if (!result.success) {
            appendLocalLog(
                profile.id,
                buildString {
                    append(TermuxCommandRunner.failureHint)
                    if (result.message.isNotBlank()) {
                        append('\n')
                        append(result.message)
                    }
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        CommandResultBus.events.collect {
            reload()
        }
    }

    when (val current = screen) {
        Screen.Onboarding -> OnboardingScreen(
            onDone = {
                repository.setOnboardingSeen()
                screen = Screen.Home
            }
        )
        Screen.Home -> HomeScreen(
            profiles = profiles,
            statusFor = repository::getStatus,
            onOpenOnboarding = { screen = Screen.Onboarding },
            onAdd = { screen = Screen.Edit(null) },
            onEdit = { screen = Screen.Edit(it.id) },
            onDelete = {
                repository.deleteProfile(it.id)
                reload()
            },
            onDetail = { screen = Screen.Detail(it.id) },
            onRun = { profile, action ->
                if (action == TermuxAction.Log) screen = Screen.Detail(profile.id)
                runAction(profile, action)
            }
        )
        is Screen.Edit -> {
            val editingProfile = profiles.firstOrNull { it.id == current.profileId }
            EditTunnelScreen(
                profile = editingProfile,
                onBack = { screen = Screen.Home },
                onSave = { name, token, note ->
                    val saved = repository.upsertProfile(
                        id = editingProfile?.id,
                        name = name,
                        token = token,
                        note = note
                    )
                    reload()
                    screen = Screen.Detail(saved.id)
                }
            )
        }
        is Screen.Detail -> {
            val profile = profiles.firstOrNull { it.id == current.profileId }
            if (profile == null) {
                EmptyFallbackScreen(onBack = { screen = Screen.Home })
            } else {
                DetailScreen(
                    profile = profile,
                    status = repository.getStatus(profile.id),
                    log = repository.getLog(profile.id),
                    refreshTick = refreshTick,
                    onBack = { screen = Screen.Home },
                    onEdit = { screen = Screen.Edit(profile.id) },
                    onRun = { runAction(profile, it) }
                )
            }
        }
    }
}

private sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data class Edit(val profileId: String?) : Screen
    data class Detail(val profileId: String) : Screen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingScreen(onDone: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("首次使用") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StepCard(
                    title = "1. 安装 Termux",
                    body = "请先安装 Termux，并手动打开一次。"
                )
            }
            item {
                StepCard(
                    title = "2. 安装 cloudflared",
                    body = "在 Termux 里执行：",
                    code = "pkg update\npkg install cloudflared"
                )
            }
            item {
                StepCard(
                    title = "3. 开启外部调用和系统权限",
                    body = "在 Termux 里执行后重启 Termux。然后到 Android 设置 > 应用 > Cloudflared Launcher > 权限 > 其他权限，允许 Run commands in Termux environment。",
                    code = "mkdir -p ~/.termux\necho \"allow-external-apps = true\" >> ~/.termux/termux.properties"
                )
            }
            item {
                StepCard(
                    title = "4. 创建 Cloudflare Tunnel",
                    body = "进入 Cloudflare Zero Trust 网页后台创建 Tunnel。Public Hostname 的 Service URL 填你的本地服务，例如：",
                    code = "http://127.0.0.1:5244"
                )
            }
            item {
                StepCard(
                    title = "5. 保存 Tunnel Token",
                    body = "复制 Cloudflare 提供的 tunnel token 到本 App。本 App 只在本机保存配置，不登录 Cloudflare，不创建云端 Tunnel，不调用 Cloudflare API。"
                )
            }
            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDone
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("我已准备好")
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    title: String,
    body: String,
    code: String? = null
) {
    ElevatedCard(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (code != null) CommandBlock(code)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(
    profiles: List<TunnelProfile>,
    statusFor: (String) -> TunnelStatus,
    onOpenOnboarding: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (TunnelProfile) -> Unit,
    onDelete: (TunnelProfile) -> Unit,
    onDetail: (TunnelProfile) -> Unit,
    onRun: (TunnelProfile, TermuxAction) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<TunnelProfile?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Cloudflared Launcher") },
                actions = {
                    IconButton(onClick = onOpenOnboarding) {
                        Icon(Icons.Default.Info, contentDescription = "首次使用")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "新建隧道")
            }
        }
    ) { padding ->
        if (profiles.isEmpty()) {
            EmptyHome(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onAdd = onAdd
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        status = statusFor(profile.id),
                        onDetail = { onDetail(profile) },
                        onRun = { onRun(profile, it) },
                        onEdit = { onEdit(profile) },
                        onDelete = { pendingDelete = profile }
                    )
                }
            }
        }
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除隧道") },
            text = { Text("确定删除“${profile.name}”吗？本地配置和 App 内日志会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(profile)
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun EmptyHome(
    modifier: Modifier,
    onAdd: () -> Unit
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("还没有隧道配置", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "添加 Tunnel Token 后，就可以通过 Termux 控制 cloudflared。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(18.dp))
        Button(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("新建隧道")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(
    profile: TunnelProfile,
    status: TunnelStatus,
    onDetail: () -> Unit,
    onRun: (TermuxAction) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        onClick = onDetail
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (profile.note.isNotBlank()) {
                        Text(
                            profile.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                StatusPill(status)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallActionButton("启动", Icons.Default.PlayArrow) { onRun(TermuxAction.Start) }
                SmallActionButton("停止", Icons.Default.PowerSettingsNew) { onRun(TermuxAction.Stop) }
                SmallActionButton("状态", Icons.Default.Info) { onRun(TermuxAction.Status) }
                SmallActionButton("日志", Icons.Default.Article) { onRun(TermuxAction.Log) }
                SmallActionButton("编辑", Icons.Default.Edit, onClick = onEdit)
                SmallActionButton("删除", Icons.Default.Delete, danger = true, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun StatusPill(status: TunnelStatus) {
    val container = when (status) {
        TunnelStatus.Running -> Color(0xFFDCFCE7)
        TunnelStatus.Stopped -> Color(0xFFFEF3C7)
        TunnelStatus.NotInstalled -> MaterialTheme.colorScheme.surfaceVariant
        TunnelStatus.Unknown -> Color(0xFFFEE2E2)
    }
    val content = when (status) {
        TunnelStatus.Running -> Color(0xFF166534)
        TunnelStatus.Stopped -> Color(0xFF92400E)
        TunnelStatus.NotInstalled -> MaterialTheme.colorScheme.onSurfaceVariant
        TunnelStatus.Unknown -> Color(0xFF991B1B)
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = status.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SmallActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val colors = if (danger) {
        ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
    } else {
        ButtonDefaults.outlinedButtonColors()
    }
    OutlinedButton(
        onClick = onClick,
        colors = colors,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTunnelScreen(
    profile: TunnelProfile?,
    onBack: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by rememberSaveable(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var token by rememberSaveable(profile?.id) { mutableStateOf(profile?.token.orEmpty()) }
    var note by rememberSaveable(profile?.id) { mutableStateOf(profile?.note.orEmpty()) }
    var showToken by rememberSaveable { mutableStateOf(false) }
    val canSave = name.trim().isNotBlank() && token.trim().isNotBlank()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (profile == null) "新建隧道" else "编辑隧道") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = name,
                onValueChange = { name = it },
                label = { Text("隧道名称") },
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = token,
                onValueChange = { token = it },
                label = { Text("Tunnel Token") },
                minLines = 3,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showToken) "隐藏 Token" else "显示 Token"
                        )
                    }
                }
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = note,
                onValueChange = { note = it },
                label = { Text("备注") },
                minLines = 3
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave,
                onClick = { onSave(name, token, note) }
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("保存")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DetailScreen(
    profile: TunnelProfile,
    status: TunnelStatus,
    log: String,
    refreshTick: Int,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRun: (TermuxAction) -> Unit
) {
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("隧道详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            profile.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(12.dp))
                        StatusPill(status)
                    }
                    Text(
                        profile.note.ifBlank { "无备注" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = { onRun(TermuxAction.Install) }, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Terminal, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("安装到 Termux")
                }
                FilledTonalButton(onClick = { onRun(TermuxAction.Start) }, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("启动 Tunnel")
                }
                FilledTonalButton(onClick = { onRun(TermuxAction.Stop) }, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("停止 Tunnel")
                }
                FilledTonalButton(onClick = { onRun(TermuxAction.Status) }, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("查看状态")
                }
                FilledTonalButton(onClick = { onRun(TermuxAction.Log) }, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Article, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("查看日志")
                }
                OutlinedButton(
                    onClick = { onRun(TermuxAction.ClearLog) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("清空日志")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { clipboard.setText(AnnotatedString(log)) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("复制日志")
                }
            }

            LogPanel(log = log, refreshTick = refreshTick)
        }
    }
}

@Composable
private fun LogPanel(log: String, refreshTick: Int) {
    val scrollState = rememberScrollState()
    LaunchedEffect(refreshTick) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        SelectionContainer {
            Text(
                text = log.ifBlank { "暂无日志" },
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmptyFallbackScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("隧道不存在") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text("这个隧道配置已经被删除。")
        }
    }
}

@Composable
private fun CommandBlock(code: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            modifier = Modifier.padding(12.dp),
            text = code,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun timestamp(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
