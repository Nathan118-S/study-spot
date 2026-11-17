package education.studyspot.app.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import education.studyspot.app.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The adaptive icon's 108 dp layers (background + foreground) at 4×, with
 * guides: the 72 dp visible area (dashed) and the 66 dp safe-zone circle
 * every launcher mask keeps. Also a circle-masked copy, as most launchers show it.
 * Written straight to build/outputs/roborazzi (it's a check, not a comparison).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = ScreenshotApp::class)
class AppIconScreenshotTest {
    @Test
    fun adaptiveIcon() {
        if (System.getProperty("roborazzi.test.record") != "true") return
        val context = ApplicationProvider.getApplicationContext<Context>()
        val px = 432 // 108 dp × 4
        val scale = px / 108f
        val out = Bitmap.createBitmap(px * 2 + 40, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.rgb(200, 200, 200))

        fun layers(c: Canvas) {
            c.drawColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!.apply { setBounds(0, 0, px, px) }.draw(c)
        }

        // Left: full layers with guides.
        val full = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        Canvas(full).also { c ->
            layers(c)
            val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.RED }
            c.drawCircle(px / 2f, px / 2f, 33 * scale, guide)
            guide.pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
            guide.color = Color.BLUE
            c.drawRect(18 * scale, 18 * scale, 90 * scale, 90 * scale, guide)
            guide.color = Color.GRAY
            c.drawLine(px / 2f, 0f, px / 2f, px.toFloat(), guide)
            c.drawLine(0f, px / 2f, px.toFloat(), px / 2f, guide)
        }
        canvas.drawBitmap(full, 0f, 0f, null)

        // Right: what a circular launcher mask shows (the 72 dp area, clipped to a circle).
        val masked = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        Canvas(masked).also { c ->
            val layer = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { layers(Canvas(it)) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.BitmapShader(layer, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
            }
            c.drawCircle(px / 2f, px / 2f, 36 * scale, paint)
        }
        canvas.drawBitmap(masked, px + 40f, 0f, null)

        File("build/outputs/roborazzi").mkdirs()
        File("build/outputs/roborazzi/19_app_icon_adaptive.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
