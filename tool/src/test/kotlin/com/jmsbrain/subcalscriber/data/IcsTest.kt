package com.jmsbrain.subcalscriber.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IcsTest {
    private val ny = ZoneId.of("America/New_York")

    private fun cal(vararg events: String) =
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" + events.joinToString("") { "BEGIN:VEVENT\r\n$it\r\nEND:VEVENT\r\n" } + "END:VCALENDAR\r\n"

    private fun expand(text: String, from: String, days: Long) =
        Ics.expand(Ics.parse(text), LocalDate.parse(from).atStartOfDay(ny), LocalDate.parse(from).plusDays(days).atStartOfDay(ny), ny)

    @Test fun singleEventWithFoldedTextAndEscapes() {
        val e = Ics.parse(cal("UID:1\r\nSUMMARY:Lunch\\, then\r\n  a walk\r\nDTSTART;TZID=America/New_York:20261014T123000\r\nDTEND;TZID=America/New_York:20261014T133000")).single()
        assertEquals("Lunch, then a walk", e.summary)
        assertEquals(LocalTime.of(12, 30), e.start.time)
    }

    @Test fun alarmDescriptionDoesNotReplaceEvent() {
        val text = cal("UID:1\r\nSUMMARY:A\r\nDESCRIPTION:real\r\nDTSTART:20261014T120000Z\r\nBEGIN:VALARM\r\nDESCRIPTION:alarm\r\nEND:VALARM")
        assertEquals("real", Ics.parse(text).single().description)
    }

    @Test fun allDayMultiDay() {
        val o = expand(cal("UID:1\r\nSUMMARY:Conf\r\nDTSTART;VALUE=DATE:20261014\r\nDTEND;VALUE=DATE:20261017"), "2026-10-01", 30).single()
        assertTrue(o.allDay)
        assertEquals(LocalDate.parse("2026-10-17"), o.end.toLocalDate())
    }

    @Test fun weeklyByDayWithCountAndExdate() {
        val text = cal("UID:1\r\nSUMMARY:Group\r\nDTSTART;TZID=America/New_York:20261005T190000\r\nDURATION:PT1H\r\nRRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=6\r\nEXDATE;TZID=America/New_York:20261012T190000")
        val days = expand(text, "2026-10-01", 60).map { it.start.toLocalDate().toString() }
        assertEquals(listOf("2026-10-05", "2026-10-07", "2026-10-14", "2026-10-19", "2026-10-21"), days)
    }

    @Test fun weeklyKeepsWallClockAcrossDst() {
        val text = cal("UID:1\r\nSUMMARY:X\r\nDTSTART;TZID=America/New_York:20261026T090000\r\nRRULE:FREQ=WEEKLY")
        assertTrue(expand(text, "2026-10-20", 21).all { it.start.withZoneSameInstant(ny).hour == 9 })
    }

    @Test fun monthlyNthWeekdayAndLastFriday() {
        val second = cal("UID:1\r\nSUMMARY:X\r\nDTSTART:20260113T170000Z\r\nRRULE:FREQ=MONTHLY;BYDAY=2TU")
        assertEquals(LocalDate.parse("2026-10-13"), expand(second, "2026-10-01", 30).single().start.toLocalDate())
        val last = cal("UID:2\r\nSUMMARY:Y\r\nDTSTART;VALUE=DATE:20260130\r\nRRULE:FREQ=MONTHLY;BYDAY=-1FR")
        assertEquals(LocalDate.parse("2026-10-30"), expand(last, "2026-10-01", 30).single().start.toLocalDate())
    }

    @Test fun yearlyBirthdayAndUntil() {
        val bday = cal("UID:1\r\nSUMMARY:B\r\nDTSTART;VALUE=DATE:19900315\r\nRRULE:FREQ=YEARLY")
        assertEquals(LocalDate.parse("2027-03-15"), expand(bday, "2027-03-01", 30).single().start.toLocalDate())
        val ended = cal("UID:2\r\nSUMMARY:E\r\nDTSTART:20260101T150000Z\r\nRRULE:FREQ=DAILY;UNTIL=20260110T150000Z")
        assertTrue(expand(ended, "2026-10-01", 30).isEmpty())
    }

    @Test fun movedAndCancelledOccurrences() {
        val text = cal(
            "UID:s\r\nSUMMARY:Standup\r\nDTSTART;TZID=America/New_York:20261012T090000\r\nRRULE:FREQ=DAILY;COUNT=3",
            "UID:s\r\nRECURRENCE-ID;TZID=America/New_York:20261013T090000\r\nSUMMARY:Standup (late)\r\nDTSTART;TZID=America/New_York:20261013T110000",
            "UID:s\r\nRECURRENCE-ID;TZID=America/New_York:20261014T090000\r\nSTATUS:CANCELLED\r\nSUMMARY:Standup\r\nDTSTART;TZID=America/New_York:20261014T090000",
        )
        val got = expand(text, "2026-10-01", 30).map { it.title to it.start.withZoneSameInstant(ny).hour }
        assertEquals(listOf("Standup" to 9, "Standup (late)" to 11), got)
    }

    @Test fun windowsAndPrefixedZoneNames() {
        assertEquals(ny, Ics.zone("Eastern Standard Time"))
        assertEquals(ny, Ics.zone("/mozilla.org/20050126_1/America/New_York"))
    }

    @Test fun linksFromUploadedText() {
        val text = "My calendars:\r\nwebcal://p01-caldav.icloud.com/published/2/abc\r\n\r\nhttps://example.com/work.ics, see also (https://example.com/b.ics).\r\n[InternetShortcut]\r\nURL=https://example.com/work.ics\r\n"
        assertEquals(
            listOf("webcal://p01-caldav.icloud.com/published/2/abc", "https://example.com/work.ics", "https://example.com/b.ics"),
            findLinks(text).map { it.url },
        )
        assertEquals(
            listOf(Link("https://a.example/x.ics", "Work"), Link("webcal://b.example/y", "Kids' school"), Link("https://c.example/z", null)),
            findLinks("Work: https://a.example/x.ics\nKids' school - webcal://b.example/y\nhttps://c.example/z"),
        )
    }

    @Test fun calendarNameFromFeed() {
        assertEquals("Family, shared", Ics.calendarName("BEGIN:VCALENDAR\r\nX-WR-CALNAME:Family\\, shared\r\nEND:VCALENDAR"))
        assertEquals(null, Ics.calendarName(cal("UID:1\r\nDTSTART:20261014T120000Z")))
    }

    @Test fun googleShareLinksBecomeIcsAddresses() {
        val ics = "https://calendar.google.com/calendar/ical/en.usa%23holiday%40group.v.calendar.google.com/public/basic.ics"
        assertEquals(ics, Calendar.googleIcsUrl("https://calendar.google.com/calendar/u/0?cid=ZW4udXNhI2hvbGlkYXlAZ3JvdXAudi5jYWxlbmRhci5nb29nbGUuY29t"))
        assertEquals(ics, Calendar.googleIcsUrl("https://calendar.google.com/calendar/embed?src=en.usa%23holiday%40group.v.calendar.google.com&ctz=America%2FNew_York"))
        assertEquals(ics, Calendar.googleIcsUrl("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fcalendar.google.com%2Fcalendar%2Fu%2F0%3Fcid%3DZW4udXNhI2hvbGlkYXlAZ3JvdXAudi5jYWxlbmRhci5nb29nbGUuY29t"))
        assertEquals(null, Calendar.googleIcsUrl("https://example.com/?cid=ZW4udXNh"))
        assertEquals("https://bit.ly/abc", Calendar.httpUrl("http://bit.ly/abc"))
    }

    @Test fun webcalBecomesHttps() {
        assertEquals("https://example.com/a.ics", Calendar.httpUrl("webcal://example.com/a.ics"))
        assertEquals("W", tagFrom(" work"))
        assertEquals("O", freeTag("Holidays", setOf("H", "W")))
        assertEquals("A", freeTag("Hh", setOf("H")))
    }
}
