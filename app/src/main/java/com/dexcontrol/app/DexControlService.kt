package com.dexcontrol.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Serviço de acessibilidade que injeta gestos (movimento do cursor, cliques,
 * scroll) no display do Samsung DeX e manipula texto no campo focado.
 *
 * O cursor só é exibido — e os controles só funcionam — quando o DeX está
 * ativo (monitor externo conectado ou modo desktop da Samsung detectado).
 * Sem DeX, o overlay é removido e nenhum gesto é injetado.
 */
class DexControlService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: DexControlService? = null
            private set

        val isRunning: Boolean
            get() = instance != null
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var cursorView: CursorView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var displayListener: DisplayManager.DisplayListener? = null

    var targetDisplayId: Int = Display.DEFAULT_DISPLAY
        private set

    val isOnExternalDisplay: Boolean
        get() = targetDisplayId != Display.DEFAULT_DISPLAY

    /** Verdadeiro apenas quando o DeX está ativo (monitor externo ou modo desktop). */
    @Volatile
    var isDexActive: Boolean = false
        private set

    private var screenWidth = 1920f
    private var screenHeight = 1080f

    var cursorX = 400f
        private set
    var cursorY = 300f
        private set

    // ---------------------------------------------------------------------
    // Ciclo de vida
    // ---------------------------------------------------------------------

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        setupForBestDisplay()

        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                mainHandler.post { setupForBestDisplay() }
            }

            override fun onDisplayRemoved(displayId: Int) {
                mainHandler.post { setupForBestDisplay() }
            }

            override fun onDisplayChanged(displayId: Int) = Unit
        }
        dm.registerDisplayListener(displayListener, mainHandler)
    }

    override fun onDestroy() {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayListener?.let { dm.unregisterDisplayListener(it) }
        removeCursorOverlay()
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /** Chamado quando o DeX é ativado/desativado sem troca de display (ex.: DeX na tela do celular). */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mainHandler.post { setupForBestDisplay() }
    }

    // ---------------------------------------------------------------------
    // Display e overlay do cursor
    // ---------------------------------------------------------------------

    /**
     * Detecta o modo desktop da Samsung (DeX) via campo de configuração
     * proprietário. Retorna false em aparelhos não-Samsung ou fora do DeX.
     */
    private fun isSamsungDexMode(): Boolean = try {
        val config = resources.configuration
        val configClass = config.javaClass
        val enabled = configClass.getField("SEM_DESKTOP_MODE_ENABLED").getInt(configClass)
        configClass.getField("semDesktopModeEnabled").getInt(config) == enabled
    } catch (_: Exception) {
        false
    }

    private fun setupForBestDisplay() {
        removeCursorOverlay()

        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val external = dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.isValid }

        // DeX ativo = monitor externo conectado OU modo desktop Samsung
        // (DeX no próprio celular / tablet).
        isDexActive = external != null || isSamsungDexMode()

        if (!isDexActive) {
            // Sem DeX: nenhum cursor na tela do celular e nenhum controle ativo.
            targetDisplayId = Display.DEFAULT_DISPLAY
            return
        }

        val display = external ?: dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return

        targetDisplayId = display.displayId
        // O ponteiro do mouse agora é o cursor real do sistema (injetado via
        // Shizuku), então não desenhamos mais um cursor sobreposto aqui.
    }

    private fun removeCursorOverlay() {
        val wm = windowManager
        val view = cursorView
        if (wm != null && view != null) {
            try {
                wm.removeViewImmediate(view)
            } catch (_: Exception) {
                // View já removida.
            }
        }
        windowManager = null
        cursorView = null
        layoutParams = null
    }

    // ---------------------------------------------------------------------
    // Mouse
    // ---------------------------------------------------------------------

    fun moveCursorBy(dx: Float, dy: Float) {
        if (!isDexActive) return
        cursorX = (cursorX + dx).coerceIn(0f, screenWidth - 1f)
        cursorY = (cursorY + dy).coerceIn(0f, screenHeight - 1f)

        val wm = windowManager
        val view = cursorView
        val params = layoutParams
        if (wm != null && view != null && params != null) {
            params.x = cursorX.toInt()
            params.y = cursorY.toInt()
            mainHandler.post {
                try {
                    wm.updateViewLayout(view, params)
                } catch (_: Exception) {
                    // Overlay pode ter sido removido durante troca de display.
                }
            }
        }
    }

    /** Clique esquerdo: toque rápido na posição do cursor. */
    fun leftClick() = dispatchTap(cursorX, cursorY, durationMs = 60)

    /** Clique direito: no DeX, um toque longo abre o menu de contexto. */
    fun rightClick() = dispatchTap(cursorX, cursorY, durationMs = 600)

    /** Clique duplo. */
    fun doubleClick() {
        dispatchTap(cursorX, cursorY, durationMs = 50)
        mainHandler.postDelayed({ dispatchTap(cursorX, cursorY, durationMs = 50) }, 140)
    }

    /** Scroll: swipe vertical a partir da posição do cursor. */
    fun scrollBy(deltaY: Float) {
        val startX = cursorX.coerceIn(8f, screenWidth - 8f)
        val startY = cursorY.coerceIn(8f, screenHeight - 8f)
        val endY = (startY - deltaY).coerceIn(8f, screenHeight - 8f)
        if (startY == endY) return

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }
        dispatchStroke(path, durationMs = 180)
    }

    /** Scroll horizontal: swipe lateral a partir da posição do cursor. */
    fun scrollHorizontalBy(deltaX: Float) {
        val startX = cursorX.coerceIn(8f, screenWidth - 8f)
        val startY = cursorY.coerceIn(8f, screenHeight - 8f)
        val endX = (startX - deltaX).coerceIn(8f, screenWidth - 8f)
        if (startX == endX) return

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, startY)
        }
        dispatchStroke(path, durationMs = 180)
    }

    private fun dispatchTap(x: Float, y: Float, durationMs: Long) {
        val path = Path().apply { moveTo(x, y) }
        dispatchStroke(path, durationMs)
    }

    private fun dispatchStroke(path: Path, durationMs: Long) {
        if (!isDexActive) return
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder()
            .setDisplayId(targetDisplayId)
            .addStroke(stroke)
            .build()
        dispatchGesture(gesture, null, null)
    }

    // ---------------------------------------------------------------------
    // Teclado / texto
    // ---------------------------------------------------------------------

    private fun focusedEditable(): AccessibilityNodeInfo? =
        findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

    /** Digita texto no campo focado (anexa ao conteúdo atual). */
    fun typeText(text: String): Boolean {
        val node = focusedEditable() ?: return false
        val current = node.text?.toString().orEmpty()
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                current + text,
            )
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Apaga o último caractere do campo focado. */
    fun backspace(): Boolean {
        val node = focusedEditable() ?: return false
        val current = node.text?.toString().orEmpty()
        if (current.isEmpty()) return false
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                current.dropLast(1),
            )
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Enter (ação IME) no campo focado. */
    fun pressEnter(): Boolean {
        val node = focusedEditable() ?: return false
        return node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    /** Ctrl + C — copia a seleção do campo focado. */
    fun copy(): Boolean =
        focusedEditable()?.performAction(AccessibilityNodeInfo.ACTION_COPY) ?: false

    /** Ctrl + V — cola no campo focado. */
    fun paste(): Boolean =
        focusedEditable()?.performAction(AccessibilityNodeInfo.ACTION_PASTE) ?: false

    /** Ctrl + X — recorta a seleção do campo focado. */
    fun cut(): Boolean =
        focusedEditable()?.performAction(AccessibilityNodeInfo.ACTION_CUT) ?: false

    /** Ctrl + A — seleciona todo o texto do campo focado. */
    fun selectAll(): Boolean {
        val node = focusedEditable() ?: return false
        val length = node.text?.length ?: return false
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, length)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
    }

    // ---------------------------------------------------------------------
    // Ações globais do sistema
    // ---------------------------------------------------------------------

    fun goBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun goHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun openRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun takeScreenshot() = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)

    // ---------------------------------------------------------------------
    // View do cursor
    // ---------------------------------------------------------------------

    /** Desenha a seta do cursor (branca com contorno escuro). */
    private class CursorView(context: Context) : View(context) {

        private val sizePx = (24 * context.resources.displayMetrics.density).toInt()

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f * context.resources.displayMetrics.density
        }

        private val arrow = Path()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(sizePx, sizePx)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val s = sizePx.toFloat()
            arrow.reset()
            arrow.moveTo(0f, 0f)
            arrow.lineTo(0f, s * 0.78f)
            arrow.lineTo(s * 0.22f, s * 0.60f)
            arrow.lineTo(s * 0.38f, s * 0.94f)
            arrow.lineTo(s * 0.50f, s * 0.88f)
            arrow.lineTo(s * 0.34f, s * 0.55f)
            arrow.lineTo(s * 0.60f, s * 0.52f)
            arrow.close()
            canvas.drawPath(arrow, fillPaint)
            canvas.drawPath(arrow, strokePaint)
        }
    }
}
