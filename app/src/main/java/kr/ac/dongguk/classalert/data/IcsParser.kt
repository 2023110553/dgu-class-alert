package kr.ac.dongguk.classalert.data

import java.time.*
import java.time.format.DateTimeFormatter

object IcsParser {
    private val seoul = ZoneId.of("Asia/Seoul")

    fun parse(raw: String): List<ParsedEvent> {
        val unfolded = raw.replace("\r\n", "\n").replace("\r", "\n").split('\n').fold(mutableListOf<String>()) { out, line ->
            if ((line.startsWith(' ') || line.startsWith('\t')) && out.isNotEmpty()) out[out.lastIndex] += line.drop(1) else out += line
            out
        }
        val result = mutableListOf<ParsedEvent>()
        var fields: MutableMap<String, Pair<String, String>>? = null
        unfolded.forEach { line ->
            when (line.trim()) {
                "BEGIN:VEVENT" -> fields = linkedMapOf()
                "END:VEVENT" -> fields?.let { build(it) }?.let(result::add).also { fields = null }
                else -> fields?.let { target ->
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        val head = line.substring(0, colon)
                        val name = head.substringBefore(';').uppercase()
                        target[name] = head to line.substring(colon + 1)
                    }
                }
            }
        }
        return result
    }

    private fun build(f: Map<String, Pair<String, String>>): ParsedEvent? {
        val uid = f["UID"]?.second?.trim().orEmpty(); if (uid.isEmpty()) return null
        val summary = unescape(f["SUMMARY"]?.second.orEmpty()).trim()
        val start = f["DTSTART"]?.let { parseDate(it.first, it.second) } ?: return null
        val lower = summary.lowercase()
        val type = when {
            lower.endsWith(" is due") -> EventType.ASSIGNMENT
            lower.endsWith(" : progress start") -> EventType.LECTURE_START
            lower.endsWith(" : progress stop") -> EventType.LECTURE_END
            lower.endsWith(" opens") || lower.endsWith(" closes") -> EventType.EXAM
            else -> EventType.OTHER
        }
        return ParsedEvent(uid, summary, unescape(f["DESCRIPTION"]?.second.orEmpty()),
            f["CATEGORIES"]?.second?.let(::unescape), type, start,
            f["DTEND"]?.let { parseDate(it.first, it.second) }, f["LAST-MODIFIED"]?.let { parseDate(it.first, it.second) })
    }

    private fun parseDate(head: String, raw: String): Long? = runCatching {
        when {
            raw.matches(Regex("\\d{8}")) -> LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(seoul).toInstant().toEpochMilli()
            raw.endsWith("Z") -> Instant.from(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX").parse(raw)).toEpochMilli()
            else -> {
                val zone = Regex("TZID=([^;:]+)").find(head)?.groupValues?.get(1)?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: seoul
                LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")).atZone(zone).toInstant().toEpochMilli()
            }
        }
    }.getOrNull()

    private fun unescape(s: String) = s.replace("\\n", "\n", true).replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}

