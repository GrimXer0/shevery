package moe.shizuku.manager.commandium

import moe.shizuku.manager.utils.AiExplainUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiNanoProviderTest {

    @Test
    fun geminiNanoPreset_isRegisteredAndValid() {
        val preset = aiProviderPresets.firstOrNull { isGeminiNano(it.baseUrl) }
        assertTrue("Gemini Nano preset should be present", preset != null)
        assertEquals(GEMINI_NANO_BASE_URL, preset!!.baseUrl)
        assertTrue(urlIsValid(GEMINI_NANO_BASE_URL))
        assertTrue(isGeminiNano("LOCAL://GEMINI-NANO"))
        assertFalse(isGeminiNano("https://generativelanguage.googleapis.com/v1beta/openai"))
    }

    @Test
    fun matchPresetName_recognizesGeminiNanoEvenIfRenamed() {
        assertEquals(
            GEMINI_NANO_PRESET_NAME,
            matchPresetName("My Pixel Nano", GEMINI_NANO_BASE_URL),
        )
    }

    @Test
    fun resolveModel_returnsGeminiNanoDefaultForLocalEndpoint() {
        assertEquals(
            GEMINI_NANO_DEFAULT_MODEL,
            AiExplainUtil.resolveModel(GEMINI_NANO_BASE_URL),
        )
    }
}
