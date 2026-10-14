package kr.ac.dongguk.classalert

import kr.ac.dongguk.classalert.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class IcsParserTest {
    @Test fun utcAndTypes() {
        val events = IcsParser.parse("""BEGIN:VCALENDAR
BEGIN:VEVENT
UID:a
SUMMARY:과제 1 is due
DTSTART:20261031T150000Z
LAST-MODIFIED:20261001T010203Z
END:VEVENT
BEGIN:VEVENT
UID:b
SUMMARY:3장 설명 : Progress start
DTSTART:20261101T000000Z
END:VEVENT
BEGIN:VEVENT
UID:c
SUMMARY:3장 설명 : Progress stop
DTSTART:20261102T000000Z
END:VEVENT
END:VCALENDAR""")
        assertEquals(3, events.size)
        assertEquals(Instant.parse("2026-10-31T15:00:00Z").toEpochMilli(), events[0].startsAt)
        assertEquals(EventType.ASSIGNMENT, events[0].type)
        assertEquals(EventType.LECTURE_START, events[1].type)
        assertEquals(EventType.LECTURE_END, events[2].type)
    }

    @Test fun foldedEscapedAndMissingUid() {
        val events = IcsParser.parse("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:folded\r\nSUMMARY:아주 긴 과제\r\n 제목 is due\r\nDESCRIPTION:첫 줄\\n둘째 줄\\, 쉼표\r\nDTSTART;TZID=Asia/Seoul:20261001T120000\r\nEND:VEVENT\r\nBEGIN:VEVENT\r\nSUMMARY:no uid\r\nDTSTART:20261001T120000Z\r\nEND:VEVENT\r\nEND:VCALENDAR")
        assertEquals(1, events.size)
        assertTrue(events.single().title.contains("제목"))
        assertEquals("첫 줄\n둘째 줄, 쉼표", events.single().description)
    }
}
