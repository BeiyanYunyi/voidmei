package voidmei.fm

import kotlin.test.*

class BlkParserTest {
    @Test fun typedFieldsNestedBlocksCommentsAndDuplicates() {
        val root = BlkParser.parse("""
            // central file
            fmFile:t="fm/p-51.blk"
            Engine { power:r=100; power:r=200 }
            Engine { type:t="Jet" }
            /* multiline
               comment */
            point:p3=1, 2, 3
            enabled:b=yes
        """.trimIndent())
        assertEquals("fm/p-51.blk", root.field("fmfile")!!.values.single())
        val engines = root.entries.filterIsInstance<BlkBlock>()
        assertEquals(2, engines.size)
        assertEquals(200.0, engines.first().field("power")!!.number())
        assertEquals(2, engines.first().entries.size)
        assertEquals(listOf("1", "2", "3"), root.field("point")!!.values)
        assertEquals(listOf("Engine.power", "Engine.power", "Engine.type"), root.fields().map { it.first }.filter { it.startsWith("Engine") })
    }

    @Test fun quotedStringsDoNotTurnBracesCommentsOrDelimitersIntoSyntax() {
        val root = BlkParser.parse(""""quoted name":t="a // b { ; ~"quoted~" }"""")
        assertEquals("a // b { ; \"quoted\" }", root.field("quoted name")!!.values.single())
    }

    @Test fun inlineFieldsAndMatrices() {
        val root = BlkParser.parse("a:r=1 b:r=2\nm:tm=[[1,0,0]\n[0,1,0]]")
        assertEquals(1.0, root.field("a")!!.number())
        assertEquals(2.0, root.field("b")!!.number())
        assertNotNull(root.field("m"))
    }

    @Test fun malformedDocumentsFailInsteadOfPublishingPartialData() {
        for (text in listOf("", "// only comment", "block { a:r=1", "}", "a:r=", "a:t=\"missing", "/* missing", "a:p3=[1,2", "{\"json\":true}")) {
            assertFails(text) { BlkParser.parse(text) }
        }
    }

    @Test fun boundedDepthAndNonFiniteNumbers() {
        assertFails { BlkParser.parse("b {".repeat(66) + "a:r=1\n" + "}".repeat(66)) }
        assertNull(BlkParser.parse("a:r=1e999").field("a")!!.number())
    }

    @Test fun dagorTildeEscapesAndArraysPreserveWindowsPaths() {
        val root = BlkParser.parse("""
            path:t="fm\test.blk"
            escaped:t="line~n~~end"
            values:i[]=[42;43;44]
        """.trimIndent())
        assertEquals("fm\\test.blk", root.field("path")!!.values.single())
        assertEquals("line\n~end", root.field("escaped")!!.values.single())
        assertEquals("i[]", root.field("values")!!.type)
        assertFails { BlkParser.parse("include \"other.blk\"") }
        assertFails { BlkParser.parse("\"@override:block\" {}") }
    }
}
