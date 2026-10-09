package com.spectro.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.spectro.app.ui.theme.SpectroTheme
import com.spectro.app.ui.theme.paletteFor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h568dp")
class ModuleNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test fun bottomModulesRemainReachableOnSmallViewportWithLargeText() {
        var opened: AppTab? = null
        compose.setContent {
            SpectroTheme("spectro") {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                    Box(Modifier.width(320.dp).height(260.dp)) {
                        MoreModulesScreen(paletteFor("spectro"), emptyList(), onToggleFavorite = {}, onOpen = { opened = it })
                    }
                }
            }
        }
        compose.onNodeWithTag("module-grid").performScrollToNode(hasTestTag("module-CLIENTS"))
        compose.onNodeWithTag("module-CLIENTS").assertIsDisplayed().performClick()
        assertEquals(AppTab.CLIENTS, opened)
        compose.onNodeWithTag("module-grid").performScrollToNode(hasTestTag("module-ZENTHA"))
        compose.onNodeWithTag("module-ZENTHA").assertIsDisplayed().performClick()
        assertEquals(AppTab.ZENTHA, opened)
        compose.onNodeWithTag("module-grid").performScrollToNode(hasTestTag("module-ECONOMIC"))
        compose.onNodeWithTag("module-ECONOMIC").performClick()
        assertEquals(AppTab.ECONOMIC, opened)
    }
    @Test fun searchingModulesOpensCorrectDestination() {
        var opened: AppTab? = null
        compose.setContent { SpectroTheme("spectro") { MoreModulesScreen(paletteFor("spectro"), emptyList(), onToggleFavorite = {}, onOpen = { opened = it }) } }
        compose.onNodeWithTag("module-search").performTextInput("ricavi")
        compose.onNodeWithTag("module-grid").performScrollToNode(hasTestTag("module-ECONOMIC"))
        compose.onNodeWithTag("module-ECONOMIC").performClick()
        assertEquals(AppTab.ECONOMIC, opened)
        compose.onNodeWithTag("module-ZENTHA").assertDoesNotExist()
    }
    @Test fun favoritesArePersistentAndEmptySelectionStaysEmpty() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("spectro_navigation", 0).edit().clear().commit()
        assertTrue(AppTab.ZENTHA in ShortcutStore.read(context))
        ShortcutStore.write(context, listOf(AppTab.CLIENTS, AppTab.ZENTHA))
        assertEquals(listOf(AppTab.CLIENTS, AppTab.ZENTHA), ShortcutStore.read(context))
        ShortcutStore.write(context, emptyList())
        assertTrue(ShortcutStore.read(context).isEmpty())
    }
    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun appNavigationOpensZenthaAndKeepsModuleListAboveBottomBar() {
        val vm = SpectroViewModel(RuntimeEnvironment.getApplication())
        val store = androidx.lifecycle.ViewModelStore().apply { put("spectro", vm) }
        try {
            compose.setContent { SpectroApp(vm) }
            compose.waitUntil(10_000) { !vm.state.loading }
            compose.onNodeWithTag("nav-MORE").assertIsDisplayed().performClick()
            compose.onNodeWithTag("module-grid").assertIsDisplayed()
            val grid = compose.onNodeWithTag("module-grid").fetchSemanticsNode().boundsInRoot
            val navigation = compose.onNodeWithTag("nav-MORE").fetchSemanticsNode().boundsInRoot
            assertTrue("Modules overlap bottom navigation", grid.bottom <= navigation.top)
            val dir = java.io.File("build/reports/ui-preview").apply { mkdirs() }
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                java.io.File(dir, "modules.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            compose.onNodeWithTag("module-grid").performScrollToNode(hasTestTag("module-ZENTHA"))
            compose.onNodeWithTag("module-ZENTHA").performClick()
            compose.onNodeWithText("ZENTHA ERP 2.0").assertIsDisplayed()
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                java.io.File(dir, "zentha.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            compose.onNodeWithTag("nav-MORE").performClick()
            compose.onNodeWithTag("module-grid").performScrollToNode(hasTestTag("module-ECONOMIC"))
            compose.onNodeWithTag("module-ECONOMIC").performClick()
            compose.onAllNodesWithText("Altri ricavi", substring = true).assertCountEquals(2)
        } finally { store.clear() }
    }

}
