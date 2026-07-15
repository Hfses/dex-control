package com.dexcontrol.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Navy = Color(0xFF0B1220)
private val Panel = Color(0xFF141F33)
private val PanelLight = Color(0xFF1D2B45)
private val Accent = Color(0xFF33C3B0)
private val TextPrimary = Color(0xFFE9EEF6)
private val TextSecondary = Color(0xFF8FA3BF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DexControlApp(openAccessibilitySettings: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var serviceRunning by remember { mutableStateOf(DexControlService.isRunning) }
    var dexActive by remember { mutableStateOf(false) }
    var sensitivity by remember { mutableFloatStateOf(2.5f) }

    // Atualiza o status do serviço periodicamente.
    LaunchedEffect(Unit) {
        while (true) {
            serviceRunning = DexControlService.isRunning
            dexActive = DexControlService.instance?.isDexActive ?: false
            delay(1000)
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
                            text = when {
                                !serviceRunning -> "Serviço desativado — ative na aba Config"
                                dexActive -> "DeX ativo — controles habilitados"
                                else -> "Aguardando o DeX — conecte ao monitor"
                            },
                            fontSize = 12.sp,
                            color = if (serviceRunning && dexActive) Accent else Color(0xFFE07E5E),
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
                    serviceRunning = serviceRunning,
                    dexActive = dexActive,
                    sensitivity = sensitivity,
                    onSensitivityChange = { sensitivity = it },
                    openAccessibilitySettings = openAccessibilitySettings,
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
                        DexControlService.instance?.moveCursorBy(
                            dragAmount.x * sensitivity,
                            dragAmount.y * sensitivity,
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { DexControlService.instance?.leftClick() },
                        onDoubleTap = { DexControlService.instance?.doubleClick() },
                        onLongPress = { DexControlService.instance?.rightClick() },
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
                        DexControlService.instance?.scrollBy(dragAmount.y * 3f)
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
                DexControlService.instance?.leftClick()
            }
            BigActionButton("Clique\ndireito", Modifier.weight(1f).fillMaxHeight()) {
                DexControlService.instance?.rightClick()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BigActionButton("Duplo clique", Modifier.weight(1f)) {
                DexControlService.instance?.doubleClick()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BigActionButton("Rolar para cima", Modifier.weight(1f)) {
                DexControlService.instance?.scrollBy(-350f)
            }
            BigActionButton("Rolar para baixo", Modifier.weight(1f)) {
                DexControlService.instance?.scrollBy(350f)
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
// Teclado
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
            "Os atalhos funcionam no campo de texto que estiver focado no DeX " +
                "(equivalentes a Ctrl+C, Ctrl+V, Ctrl+X e Ctrl+A).",
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
// Sistema
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
            ShortcutButton("Voltar", Modifier.weight(1f)) { DexControlService.instance?.goBack() }
            ShortcutButton("Início", Modifier.weight(1f)) { DexControlService.instance?.goHome() }
            ShortcutButton("Recentes", Modifier.weight(1f)) { DexControlService.instance?.openRecents() }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShortcutButton("Notificações", Modifier.weight(1f)) {
                DexControlService.instance?.openNotifications()
            }
            ShortcutButton("Capturar tela", Modifier.weight(1f)) {
                DexControlService.instance?.takeScreenshot()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Config — ativação do serviço, status do DeX, sensibilidade e guia de uso
// ---------------------------------------------------------------------------

@Composable
private fun ConfigScreen(
    serviceRunning: Boolean,
    dexActive: Boolean,
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    openAccessibilitySettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Status e ativação do serviço
        Card(
            colors = CardDefaults.cardColors(containerColor = PanelLight),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (serviceRunning) "Serviço ativado" else "Ativação necessária",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (serviceRunning) {
                        "O serviço de acessibilidade do DeX Control está em execução."
                    } else {
                        "Para controlar o cursor do DeX, ative o serviço de acessibilidade " +
                            "\u201CDeX Control\u201D nas configurações do Android."
                    },
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
                Button(
                    onClick = openAccessibilitySettings,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Navy),
                ) {
                    Text(if (serviceRunning) "Configurações de acessibilidade" else "Ativar serviço")
                }
            }
        }

        // Status do DeX
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (dexActive) "DeX ativo" else "DeX não detectado",
                    color = if (dexActive) Accent else Color(0xFFE07E5E),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (dexActive) {
                        "O monitor está conectado e os controles estão habilitados."
                    } else {
                        "O cursor e os controles ficam desativados até o Samsung DeX ser " +
                            "iniciado. Conecte o celular a um monitor (ou ative o DeX) e o " +
                            "controle será habilitado automaticamente."
                    },
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
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
                    "1. Conecte o celular a um monitor e ative o Samsung DeX.\n" +
                        "2. Ative o serviço de acessibilidade acima.\n" +
                        "3. O cursor aparece no monitor — use a aba Touchpad para mover e clicar.\n" +
                        "4. A faixa lateral do Touchpad rola as páginas (scroll).\n" +
                        "5. Para digitar, clique em um campo de texto no monitor e use a aba Teclado.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}
