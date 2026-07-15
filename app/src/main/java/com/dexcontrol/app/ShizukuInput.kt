package com.dexcontrol.app

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

/**
 * Injeção de eventos de mouse/teclado usando o privilégio de shell do Shizuku.
 *
 * Diferente do AccessibilityService (que o Android restringe na tela do DeX),
 * o Shizuku roda com identidade de shell (uid 2000), que possui a permissão
 * INJECT_EVENTS. Isso permite mover o cursor real do sistema, clicar e rolar
 * em qualquer tela — inclusive o monitor do Samsung DeX.
 *
 * Como usamos eventos com origem SOURCE_MOUSE, o próprio Android desenha e
 * move o ponteiro do mouse; não é preciso um cursor desenhado por cima.
 */
object ShizukuInput {

    private const val TAG = "ShizukuInput"
    private const val PERMISSION_REQUEST_CODE = 4210

    private var appContext: Context? = null

    // Interface IInputManager obtida via binder do Shizuku (reflexão).
    private var inputManager: Any? = null
    private var injectMethod: Method? = null

    // MotionEvent/KeyEvent.setDisplayId é oculto — resolvido por reflexão.
    // Lazy para garantir que só é resolvido após liberar as APIs ocultas.
    private val setDisplayIdMethod: Method? by lazy {
        try {
            InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
        } catch (e: Exception) {
            Log.w(TAG, "setDisplayId indisponível: ${e.message}")
            null
        }
    }

    // Estado do cursor virtual.
    @Volatile var displayId: Int = Display.DEFAULT_DISPLAY
        private set
    private var width = 1920f
    private var height = 1080f
    private var cursorX = 960f
    private var cursorY = 540f

    @Volatile private var permissionGranted = false

    // -----------------------------------------------------------------
    // Inicialização e permissão
    // -----------------------------------------------------------------

    fun init(context: Context) {
        appContext = context.applicationContext
        try {
            HiddenApiBypass.addHiddenApiExemptions("")
        } catch (e: Throwable) {
            Log.w(TAG, "HiddenApiBypass falhou: ${e.message}")
        }

        Shizuku.addBinderReceivedListenerSticky {
            if (checkPermission()) bindInputManager()
        }
        Shizuku.addBinderDeadListener {
            inputManager = null
            injectMethod = null
        }
        Shizuku.addRequestPermissionResultListener { code, result ->
            if (code == PERMISSION_REQUEST_CODE) {
                permissionGranted = result == PackageManager.PERMISSION_GRANTED
                if (permissionGranted) bindInputManager()
            }
        }
    }

    /** O app Shizuku está instalado e o serviço em execução? */
    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Exception) {
        false
    }

    fun checkPermission(): Boolean = try {
        if (!isAvailable()) {
            false
        } else if (Shizuku.isPreV11()) {
            false
        } else {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    } catch (e: Exception) {
        false
    }

    fun requestPermission() {
        try {
            if (isAvailable() && !Shizuku.isPreV11()) {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "requestPermission: ${e.message}")
        }
    }

    /** Pronto para injetar: Shizuku ativo, permissão concedida e binder vinculado. */
    fun isReady(): Boolean {
        if (!checkPermission()) return false
        if (inputManager == null) bindInputManager()
        return inputManager != null && injectMethod != null
    }

    private fun bindInputManager() {
        try {
            val binder: IBinder = ShizukuBinderWrapper(
                SystemServiceHelper.getSystemService("input"),
            )
            val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
            val asInterface = stub.getMethod("asInterface", IBinder::class.java)
            val im = asInterface.invoke(null, binder)
            inputManager = im
            injectMethod = im!!.javaClass.methods.firstOrNull { m ->
                m.name == "injectInputEvent" &&
                    m.parameterTypes.isNotEmpty() &&
                    InputEvent::class.java.isAssignableFrom(m.parameterTypes[0])
            }
            Log.i(TAG, "IInputManager vinculado (inject=${injectMethod != null})")
        } catch (e: Exception) {
            Log.e(TAG, "bindInputManager: ${e.message}")
            inputManager = null
            injectMethod = null
        }
    }

    // -----------------------------------------------------------------
    // Display alvo (monitor do DeX)
    // -----------------------------------------------------------------

    /** Atualiza a tela alvo: prioriza o monitor externo (DeX) se houver. */
    fun refreshDisplay() {
        val ctx = appContext ?: return
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val external = dm.displays.firstOrNull {
            it.displayId != Display.DEFAULT_DISPLAY && it.isValid
        }
        val display = external ?: dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return

        val newId = display.displayId
        if (newId != displayId) {
            @Suppress("DEPRECATION")
            val point = android.graphics.Point()
            @Suppress("DEPRECATION")
            display.getRealSize(point)
            width = point.x.toFloat().coerceAtLeast(1f)
            height = point.y.toFloat().coerceAtLeast(1f)
            cursorX = width / 2f
            cursorY = height / 2f
            displayId = newId
        }
    }

    fun hasExternalDisplay(): Boolean {
        val ctx = appContext ?: return false
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return dm.displays.any { it.displayId != Display.DEFAULT_DISPLAY && it.isValid }
    }

    // -----------------------------------------------------------------
    // Controles de mouse
    // -----------------------------------------------------------------

    fun moveBy(dx: Float, dy: Float) {
        if (!isReady()) return
        cursorX = (cursorX + dx).coerceIn(0f, width - 1f)
        cursorY = (cursorY + dy).coerceIn(0f, height - 1f)
        val t = SystemClock.uptimeMillis()
        inject(mouse(t, t, MotionEvent.ACTION_HOVER_MOVE, 0))
    }

    fun leftClick() = click(MotionEvent.BUTTON_PRIMARY)

    fun rightClick() = click(MotionEvent.BUTTON_SECONDARY)

    fun doubleClick() {
        click(MotionEvent.BUTTON_PRIMARY)
        click(MotionEvent.BUTTON_PRIMARY)
    }

    private fun click(button: Int) {
        if (!isReady()) return
        val down = SystemClock.uptimeMillis()
        inject(mouse(down, down, MotionEvent.ACTION_HOVER_MOVE, 0))
        inject(mouse(down, down, MotionEvent.ACTION_DOWN, button))
        val up = SystemClock.uptimeMillis()
        inject(mouse(down, up, MotionEvent.ACTION_UP, 0, buttonUp = button))
    }

    /** Scroll vertical: positivo rola para cima (roda do mouse). */
    fun scrollVertical(amount: Float) {
        if (!isReady()) return
        val t = SystemClock.uptimeMillis()
        inject(mouse(t, t, MotionEvent.ACTION_SCROLL, 0, vScroll = amount))
    }

    fun scrollHorizontal(amount: Float) {
        if (!isReady()) return
        val t = SystemClock.uptimeMillis()
        inject(mouse(t, t, MotionEvent.ACTION_SCROLL, 0, hScroll = amount))
    }

    private fun mouse(
        downTime: Long,
        eventTime: Long,
        action: Int,
        buttonState: Int,
        buttonUp: Int = 0,
        vScroll: Float = 0f,
        hScroll: Float = 0f,
    ): MotionEvent {
        val props = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coords = MotionEvent.PointerCoords().apply {
            x = cursorX
            y = cursorY
            pressure = if (buttonState != 0) 1f else 0f
            size = 1f
            if (vScroll != 0f) setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll)
            if (hScroll != 0f) setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll)
        }
        val event = MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1,
            arrayOf(props),
            arrayOf(coords),
            0,
            buttonState,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_MOUSE,
            0,
        )
        setDisplay(event)
        return event
    }

    // -----------------------------------------------------------------
    // Teclado / navegação por teclas de sistema
    // -----------------------------------------------------------------

    fun pressKey(keyCode: Int) {
        if (!isReady()) return
        val down = SystemClock.uptimeMillis()
        inject(key(down, down, KeyEvent.ACTION_DOWN, keyCode))
        val up = SystemClock.uptimeMillis()
        inject(key(down, up, KeyEvent.ACTION_UP, keyCode))
    }

    private fun key(downTime: Long, eventTime: Long, action: Int, code: Int): KeyEvent {
        val event = KeyEvent(
            downTime,
            eventTime,
            action,
            code,
            0,
            0,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            0,
            0,
            InputDevice.SOURCE_KEYBOARD,
        )
        setDisplay(event)
        return event
    }

    // -----------------------------------------------------------------
    // Injeção
    // -----------------------------------------------------------------

    private fun setDisplay(event: InputEvent) {
        try {
            setDisplayIdMethod?.invoke(event, displayId)
        } catch (e: Exception) {
            // Sem setDisplayId, cai na tela padrão.
        }
    }

    private fun inject(event: InputEvent) {
        val im = inputManager
        val method = injectMethod
        if (im == null || method == null) {
            if (event is MotionEvent) event.recycle()
            return
        }
        try {
            val args = arrayOfNulls<Any>(method.parameterTypes.size)
            args[0] = event
            for (i in 1 until args.size) args[i] = 0
            method.invoke(im, *args)
        } catch (e: Exception) {
            Log.e(TAG, "inject: ${e.message}")
        } finally {
            if (event is MotionEvent) event.recycle()
        }
    }
}
