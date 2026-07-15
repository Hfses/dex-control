package com.dexcontrol.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Navy = Color(0xFF0B1220)
private val Panel = Color(0xFF141F33)
private val PanelLight = Color(0xFF1D2B45)
private val Accent = Color(0xFF33C3B0)
private val Warning = Color(0xFFE07E5E)
private val TextPrimary = Color(0xFFE9EEF6)
private val TextSecondary = Color(0xFF8FA3BF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShizukuInput.init(applicationContext)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    background = Navy,
                    surface = Panel,
                    onPrimary = Navy,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary,
                ),
            ) {
                DexControlApp(
                    openAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    openUrl = { url ->
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: Exception) {
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DexControlApp(
    openAccessibilitySettings: () -> Unit,
    openUrl: (String) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var accessibilityOn by remember { mutableStateOf(DexControlService.isRunning) }
    var shizukuReady by remember { mutableStateOf(false) }
    var shizukuInstalled by remember { mutableStateOf(false) }
    var sensitivity by remember { mutableFloatStateOf(2.5f) }

    // Atualiza status e a tela alvo periodicamente.
    LaunchedEffect(Unit) {
        while (true) {
            accessibilityOn = DexControlService.isRunning
            shizukuInstalled = ShizukuInput.isAvailable()
            shizukuReady = ShizukuInput.isReady()
            if (shizukuReady) ShizukuInput.refreshDisplay()
            delay(1500)
        }
    }

    Scaffold(
        containerColor = Navy,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Navy,
                    titleContentColor = TextPrimary,
                ),
                title = {
                    Column {
                        Text("DeX Control", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            text = if (shizukuReady) {
                                "Shizuku pronto — controles ativos"
                            } else {
                                "Ative o Shizuku na aba Config"
                            },
                            fontSize = 12.sp,
                            color = if (shizukuReady) Accent else Warning,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Navy,
                contentColor = Accent,
                edgePadding = 8.dp,
            ) {
                listOf("Touchpad", "Mouse", "Teclado", "Sistema", "Config").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                color = if (selectedTab == index) Accent else TextSecondary,
                            )
                        },
                    )
                }
            }

            when (selectedTab) {
                0 -> TouchpadScreen(sensitivity)
                1 -> MouseScreen()
                2 -> KeyboardScreen()
                3 -> SystemScreen()
                else -> ConfigScreen(
                    accessibilityOn = accessibilityOn,
                    shizukuInstalled = shizukuInstalled,
                    shizukuReady = shizukuReady,
                    sensitivity = sensitivity,
                    onSensitivityChange = { sensitivity = it },
                    openAccessibilitySettings = openAccessibilitySettings,
                    openUrl = openUrl,
                    requestShizuku = { ShizukuInput.requestPermission() },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Touchpad — tela inteira: superfície do touchpad + faixa de scroll ao lado
// ---------------------------------------------------------------------------

@Composable
private fun TouchpadScreen(sensitivity: Float) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Superfície principal do touchpad
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Panel, RoundedCornerShape(20.dp))
                .pointerInput(sensitivity) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        ShizukuInput.moveBy(
                            dragAmount.x * sensitivity,
                            dragAmount.y * sensitivity,
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { ShizukuInput.leftClick() },
                        onDoubleTap = { ShizukuInput.doubleClick() },
                        onLongPress = { ShizukuInput.rightClick() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Arraste para mover o cursor\n\nToque = clique esquerdo\nToque duplo = duplo clique\nToque longo = clique direito",
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )
        }

        // Faixa de scroll vertical, como em um notebook
        Box(
            modifier = Modifier
                .width(64.dp)
                .fillMaxHeight()
                .background(PanelLight, RoundedCornerShape(20.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        // Arrastar para baixo rola para baixo.
                        ShizukuInput.scrollVertical(-dragAmount.y / 40f)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "S\nC\nR\nO\nL\nL",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Mouse — botões grandes de clique
// ---------------------------------------------------------------------------

@Composable
private fun MouseScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BigActionButton("Clique\nesquerdo", Modifier.weight(1f).fillMaxHeight()) {
                ShizukuInput.leftClick()
            }
            BigActionButton("Clique\ndireito", Modifier.weight(1f).fillMaxHeight()) {
                ShizukuInput.rightClick()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BigActionButton("Duplo clique", Modifier.weight(1f)) {
                ShizukuInput.doubleClick()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BigActionButton("Rolar para cima", Modifier.weight(1f)) {
                ShizukuInput.scrollVertical(3f)
            }
            BigActionButton("Rolar para baixo", Modifier.weight(1f)) {
                ShizukuInput.scrollVertical(-3f)
            }
        }
    }
}

@Composable
private fun BigActionButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PanelLight, contentColor = TextPrimary),
    ) {
        Text(label, fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
    }
}

// ---------------------------------------------------------------------------
// Teclado — texto via serviço de acessibilidade
// ---------------------------------------------------------------------------

@Composable
private fun KeyboardScreen() {
    var text by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Clique em um campo de texto no DeX com o touchpad e digite aqui:",
            color = TextSecondary,
            fontSize = 13.sp,
        )

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Digite o texto…", color = TextSecondary) },
            minLines = 4,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (text.isNotEmpty() && DexControlService.instance?.typeText(text) == true) {
                        text = ""
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Navy),
            ) {
                Text("Enviar texto")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { DexControlService.instance?.backspace() },
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Text("Backspace", color = TextPrimary)
            }
            OutlinedButton(
                onClick = { DexControlService.instance?.pressEnter() },
                modifier = Modifier.weight(1f).height(56.dp),
            ) {
                Text("Enter", color = TextPrimary)
            }
        }

        Text("Atalhos de edição", color = TextPrimary, fontWeight = FontWeight.SemiBold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShortcutButton("Copiar", Modifier.weight(1f)) { DexControlService.instance?.copy() }
            ShortcutButton("Colar", Modifier.weight(1f)) { DexControlService.instance?.paste() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShortcutButton("Recortar", Modifier.weight(1f)) { DexControlService.instance?.cut() }
            ShortcutButton("Sel. tudo", Modifier.weight(1f)) { DexControlService.instance?.selectAll() }
        }

        Text(
            "O teclado usa o serviço de acessibilidade (ative-o na aba Config). " +
                "Os atalhos funcionam no campo de texto focado no DeX.",
            color = TextSecondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ShortcutButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PanelLight, contentColor = TextPrimary),
    ) {
        Text(label, fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------------------
// Sistema — teclas de navegação injetadas via Shizuku
// ---------------------------------------------------------------------------

@Composable
private fun SystemScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ações do sistema", color = TextPrimary, fontWeight = FontWeight.SemiBold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShortcutButton("Voltar", Modifier.weight(1f)) {
                ShizukuInput.pressKey(KeyEvent.KEYCODE_BACK)
            }
            ShortcutButton("Início", Modifier.weight(1f)) {
                ShizukuInput.pressKey(KeyEvent.KEYCODE_HOME)
            }
            ShortcutButton("Recentes", Modifier.weight(1f)) {
                ShizukuInput.pressKey(KeyEvent.KEYCODE_APP_SWITCH)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShortcutButton("Volume +", Modifier.weight(1f)) {
                ShizukuInput.pressKey(KeyEvent.KEYCODE_VOLUME_UP)
            }
            ShortcutButton("Volume -", Modifier.weight(1f)) {
                ShizukuInput.pressKey(KeyEvent.KEYCODE_VOLUME_DOWN)
            }
        }

        Text(
            "As teclas de sistema são injetadas via Shizuku. Se o DeX estiver em um " +
                "monitor externo, elas afetam a tela do DeX.",
            color = TextSecondary,
            fontSize = 12.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// Config — Shizuku, acessibilidade, sensibilidade e guia de uso
// ---------------------------------------------------------------------------

@Composable
private fun ConfigScreen(
    accessibilityOn: Boolean,
    shizukuInstalled: Boolean,
    shizukuReady: Boolean,
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    openAccessibilitySettings: () -> Unit,
    openUrl: (String) -> Unit,
    requestShizuku: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Shizuku — controle do mouse/scroll
        Card(
            colors = CardDefaults.cardColors(containerColor = PanelLight),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        shizukuReady -> "1. Shizuku conectado"
                        shizukuInstalled -> "1. Shizuku instalado — conceda a permissão"
                        else -> "1. Shizuku necessário"
                    },
                    color = if (shizukuReady) Accent else Warning,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "O controle do mouse (mover, clicar, rolar) usa o Shizuku, que dá " +
                        "permissão de sistema sem root. Instale o app Shizuku, inicie o " +
                        "serviço (por Wireless Debugging) e conceda a permissão ao DeX Control.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
                if (!shizukuInstalled) {
                    Button(
                        onClick = { openUrl("https://shizuku.rikka.app/download/") },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Navy),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Instalar Shizuku")
                    }
                    OutlinedButton(
                        onClick = { openUrl("https://shizuku.rikka.app/guide/setup/") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Como configurar o Shizuku", color = TextPrimary)
                    }
                } else if (!shizukuReady) {
                    Button(
                        onClick = requestShizuku,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Navy),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Conceder permissão")
                    }
                }
            }
        }

        // Diagnóstico — teste e relatório de estado
        var report by remember { mutableStateOf("") }
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Diagnóstico", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "Conecte ao DeX, toque em Testar e observe se o cursor se move e " +
                        "clica no monitor. O relatório abaixo mostra o que está falhando.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
                Button(
                    onClick = { report = ShizukuInput.runTest() },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Navy),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Testar e diagnosticar")
                }
                if (report.isNotEmpty()) {
                    Text(
                        report,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        // Acessibilidade — teclado
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (accessibilityOn) "3. Acessibilidade ativa (teclado)" else "3. Acessibilidade (teclado)",
                    color = if (accessibilityOn) Accent else TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Opcional: ative o serviço de acessibilidade para digitar texto e usar " +
                        "os atalhos de edição na aba Teclado.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
                Button(
                    onClick = openAccessibilitySettings,
                    colors = ButtonDefaults.buttonColors(containerColor = PanelLight, contentColor = TextPrimary),
                ) {
                    Text(if (accessibilityOn) "Abrir acessibilidade" else "Ativar teclado")
                }
            }
        }

        // Sensibilidade
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Sensibilidade do cursor: ${"%.1f".format(sensitivity)}x",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
                Slider(
                    value = sensitivity,
                    onValueChange = onSensitivityChange,
                    valueRange = 0.5f..6f,
                )
            }
        }

        // Guia de uso
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Como usar", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "1. Instale o Shizuku e inicie o serviço (uma vez por reinício do celular).\n" +
                        "2. Conceda a permissão ao DeX Control (botão acima).\n" +
                        "3. Conecte o celular ao monitor e ative o Samsung DeX.\n" +
                        "4. Use a aba Touchpad para mover o cursor real do DeX e clicar.\n" +
                        "5. Para digitar, ative a acessibilidade e use a aba Teclado.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}
