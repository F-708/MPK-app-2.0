package com.politex.promo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.politex.ui.components.CollegeLogo
import com.politex.ui.components.MainHeaderBanner
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.MyApplicationTheme
import com.politex.ui.theme.applyAppTheme
import com.politex.util.AppThemeStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)

@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxxhdpi")
class CollegeLogoShotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val outDir: File = run {
        val cwd = File(System.getProperty("user.dir"))
        val root = if (cwd.name == "app") cwd.parentFile else cwd
        File(root, "promo/shots").apply { mkdirs() }
    }

    @Test
    fun logoLight() = shotLogo("logo_vector_light", dark = false)

    @Test
    fun logoDark() = shotLogo("logo_vector_dark", dark = true)

    @Test
    fun bannerLight() = shotBanner("banner_vector_light", dark = false)

    @Test
    fun bannerDark() = shotBanner("banner_vector_dark", dark = true)

    private fun shotLogo(
        name: String,
        dark: Boolean,
    ) {
        withTheme(dark) {
            composeRule.setContent {
                MyApplicationTheme {
                    Box(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(ColorBgMain)
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                    ) {
                        CollegeLogo(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .testTag("logo"),
                        )
                    }
                }
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("logo").captureRoboImage(File(outDir, "$name.png").absolutePath)
        }
    }

    private fun shotBanner(
        name: String,
        dark: Boolean,
    ) {
        withTheme(dark) {
            composeRule.setContent {
                MyApplicationTheme {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        MainHeaderBanner()
                    }
                }
            }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage(File(outDir, "$name.png").absolutePath)
        }
    }

    private fun withTheme(
        dark: Boolean,
        block: () -> Unit,
    ) {
        AppThemeStore.setDark(context, dark)
        applyAppTheme(AppTheme.COLLEGE, dark = dark)
        try {
            block()
        } finally {
            AppThemeStore.setDark(context, false)
            applyAppTheme(AppTheme.COLLEGE, dark = false)
        }
    }
}
