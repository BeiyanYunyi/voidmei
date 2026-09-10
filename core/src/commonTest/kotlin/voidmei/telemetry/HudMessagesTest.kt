package voidmei.telemetry

import kotlin.test.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest

class HudMessagesTest {
    @Test fun jsonEscapesAndIndependentIdsArePreserved() {
        val rows = HudMessageParser.parse("""{"damage":[{"msg":"发动机 \"过热\"","id":1}],"events":[{"id":1,"msg":"a\nb"}]}""")
        assertEquals(listOf(HudMessageKind.EVENT, HudMessageKind.DAMAGE), rows.map { it.kind })
        assertEquals("a\nb", rows[0].text)
        assertEquals("发动机 \"过热\"", rows[1].text)
        for (bad in listOf("{}", """{"events":[],"damage":[{"id":"1","msg":"x"}]}""",
            """{"events":[],"damage":[{"id":1,"msg":"x"},{"id":1,"msg":"y"}]}""")) {
            assertFails { HudMessageParser.parse(bad) }
        }
    }

    @Test fun retriesKeepCursorsAndDoNotDuplicateMessages() = runTest {
        val paths = mutableListOf<String>()
        val responses = listOf(
            """{"events":[{"id":3,"msg":"event"}],"damage":[{"id":7,"msg":"damage"}]}""",
            "broken",
            """{"events":[{"id":3,"msg":"event"},{"id":4,"msg":"next"}],"damage":[]}""")
        val poller = HudMessagePoller(TelemetryTransport { path -> paths += path; responses[paths.lastIndex] })
        val states = poller.states().take(4).toList()
        assertEquals(listOf("/hudmsg?lastEvt=0&lastDmg=0", "/hudmsg?lastEvt=3&lastDmg=7", "/hudmsg?lastEvt=3&lastDmg=7"), paths)
        assertEquals(states[1].messages, states[2].messages)
        assertNotNull(states[2].error)
        assertNull(states[3].error)
        assertEquals(listOf("event", "damage", "next"), states[3].messages.map { it.text })
    }

    @Test fun historyIsBoundedAndEachCollectionStartsFresh() = runTest {
        val body = "{\"events\":[],\"damage\":[" + (1..250).joinToString(",") { "{\"id\":$it,\"msg\":\"$it\"}" } + "]}"
        val paths = mutableListOf<String>()
        val poller = HudMessagePoller(TelemetryTransport { paths += it; body })
        repeat(2) {
            val last = poller.states().take(2).last()
            assertEquals(200, last.messages.size)
            assertEquals(51, last.messages.first().id)
        }
        assertTrue(paths.all { it == "/hudmsg?lastEvt=0&lastDmg=0" })
    }
}
