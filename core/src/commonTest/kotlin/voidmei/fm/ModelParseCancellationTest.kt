package voidmei.fm

import kotlin.test.*
import kotlinx.coroutines.CancellationException

class ModelParseCancellationTest {
    @Test fun cancelsInsideLongStringsCommentsAndWhitespace() {
        val long = "a".repeat(100_000)
        val sources = listOf("name:t=\"$long\"", "/*$long*/ mass:r=1000", "//$long\nmass:r=1000",
            "{\"name\":\"$long\"}", "{" + " ".repeat(100_000) + "\"mass\":1000}")
        sources.forEach { source ->
            var calls = 0
            val cancelled = CancellationException("superseded model")
            assertSame(cancelled, assertFailsWith<CancellationException> {
                FlightModelDocument.parse(source) { if (++calls == 4) throw cancelled }
            })
            assertEquals(4, calls)
            var completedChecks = 0
            assertEquals(FlightModelDocument.parse(source), FlightModelDocument.parse(source) { completedChecks++ })
            assertTrue(completedChecks > 4)
        }
    }
}
