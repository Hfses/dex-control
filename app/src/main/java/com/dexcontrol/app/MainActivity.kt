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
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
    var externalDisplay by remember { mutableStateOf(false) }

    // Atualiza o status do serviço periodicamente.
    LaunchedEffect(Unit) {
        while (true) {
            serviceRunning = DexControlService.isRunning
            externalDisplay = DexControlService.instance?.isOnExternalDisplay ?: false
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
                                !serviceRunning -> "Serviço desativado — toque em Ativar"
                                externalDisplay -> "Conectado ao monitor DeX"
                                else -> "Controlando a tela do celular"
                            },
                            fontSize = 12.sp,
                            color = if (serviceRunning) Accent else Color(0xFFE07E5E),
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
            if (!serviceRunning) {
                EnableServiceCard(openAccessibilitySettings)
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Navy,
                contentColor = Accent,
            ) {
                listOf("Touchpad", "Teclado", "Sistema").forEachIndexed { index, title ->
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
                0 -> TouchpadScreen()
                1 -> KeyboardScreen()
                else -> SystemScreen()
            }
        }
    }
}

@Composable
private fun EnableServiceCard(openAccessibilitySettings: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = PanelLight),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Ativação necessária",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Para controlar o cursor do DeX, ative o serviço de acessibilidade " +
                    "\u201CDeX Control\u201D nas configurações do Android.",
                color = TextSecondary,
                fontSize = 13.sp,
            )
            Button(
                onClick = openAccessibilitySettings,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Navy),
            ) {
                Text("Ativar serviço")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Touchpad
// ---------------------------------------------------------------------------

@Composable
private fun TouchpadScreen() {
    var sensitivity by remember { mutableFloatStateOf(2.5f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Área principal do touchpad.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Panel, RoundedCornerShape(16.dp))
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
                    "Arraste para mover o cursor\nToque = clique • Toque longo = clique direito",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }

            // Faixa de scroll vertical.
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .background(PanelLight, RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            DexControlService.instance?.scrollBy(dragAmount.y * 3f)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("S\nC\nR\nO\nL\nL", color = TextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
            }
        }

        // Botões do mouse.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MouseButton("Clique esquerdo", Modifier.weight(2f)) {
                DexControlService.instance?.leftClick()
            }
            MouseButton("Duplo", Modifier.weight(1f)) {
                DexControlService.instance?.doubleClick()
            }
            MouseButton("Clique direito", Modifier.weight(2f)) {
                DexControlService.instance?.rightClick()
            }
        }

        // Sensibilidade.
        Column {
            Text(
                "Sensibilidade do cursor: ${"%.1f".format(sensitivity)}x",
                color = TextSecondary,
                fontSize = 13.sp,
            )
            Slider(
                value = sensitivity,
                onValueChange = { sensitivity = it },
                valueRange = 0.5f..6f,
            )
        }
    }
}

@Composable
private fun MouseButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PanelLight, contentColor = TextPrimary),
    ) {
        Text(label, fontSize = 13.sp)
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
            minLines = 2,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (text.isNotEmpty() && DexControlService.instance?.typeText(text) == true) {
                        text = ""
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Navy),
            ) {
                Text("Enviar texto")
            }
            OutlinedButton(
                onClick = { DexControlService.instance?.backspace() },
                modifier = Modifier.weight(1f),
            ) {
                Text("Backspace", color = TextPrimary)
            }
            OutlinedButton(
                onClick = { DexControlService.instance?.pressEnter() },
                modifier = Modifier.weight(1f),
            ) {
                Text("Enter", color = TextPrimary)
            }
        }

        Text("Atalhos de edição", color = TextPrimary, fontWeight = FontWeight.SemiBold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShortcutButton("Copiar", Modifier.weight(1f)) { DexControlService.instance?.copy() }
            ShortcutButton("Colar", Modifier.weight(1f)) { DexControlService.instance?.paste() }
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
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PanelLight, contentColor = TextPrimary),
    ) {
        Text(label, fontSize = 12.sp)
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

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Como usar", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "1. Conecte o celular a um monitor e ative o Samsung DeX.\n" +
                        "2. Ative o serviço de acessibilidade do DeX Control.\n" +
                        "3. O cursor aparece no monitor — use a aba Touchpad para mover e clicar.\n" +
                        "4. Para digitar, clique em um campo de texto no monitor e use a aba Teclado.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}
