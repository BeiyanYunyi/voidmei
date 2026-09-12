package voidmei.config

import kotlin.test.*

class LegacyModelCategoryTest {
    private fun read(target: String, type: String, value: String) = LegacySettingsReader.read("""(panel p
        (item x :target $target :type $type :value $value))""")
    @Test fun allMappingsRespectPolarityAndOnlyTouchSelectedCategories() {
        for (category in LegacyModelCategory.entries) for (type in listOf("switch", "switch-inv")) for (value in listOf(false, true)) {
            val parsed = read(category.target, type, "$value")
            val visible = if (type == "switch-inv") !value else value
            assertEquals(mapOf(category.section to visible), parsed.modelSectionChoices)
            assertFalse(parsed.hasChanges)
            assertTrue(parsed.unmigrated.isEmpty())
            val current = AppSettings(hiddenModelSections = setOf(ModelDetailSection.RAW, ModelDetailSection.WEIGHT, ModelDetailSection.STALL))
            assertEquals(current, parsed.applyTo(current))
            val enabled = parsed.copy(importModelSections = true)
            assertTrue(enabled.hasChanges)
            val expected = current.copy(hiddenModelSections = if (visible) current.hiddenModelSections - category.section else current.hiddenModelSections + category.section)
            assertEquals(expected, enabled.applyTo(current))
            assertEquals(expected, enabled.applyTo(expected))
            assertEquals(expected, SettingsJson.decode(SettingsJson.encode(expected)))
            assertTrue(ModelDetailSection.STALL in expected.hiddenModelSections)
        }
    }
    @Test fun invalidAndDuplicateFlagsFailWhileUnsupportedCategoriesStayReported() {
        for (category in LegacyModelCategory.entries) {
            for (bad in listOf("TRUE", "1", "null")) assertFails { read(category.target, "switch", bad) }
            assertFails { read(category.target, "data", "true") }
            assertFails { LegacySettingsReader.read("""(panel p
                (item a :target ${category.target} :type switch :value true)
                (item b :target ${category.target} :type switch :value false))""") }
        }
        val parsed = read("showDrag", "switch", "true")
        assertTrue(parsed.modelSectionChoices.isEmpty())
        assertEquals("showDrag", parsed.unmigrated.single().target)
    }
}
