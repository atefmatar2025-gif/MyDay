package com.dayflow.core

import com.dayflow.core.TestData.TODAY
import com.dayflow.core.backup.BackupCodec
import com.dayflow.core.backup.BackupCrypto
import com.dayflow.core.backup.BackupFormatException
import com.dayflow.core.backup.BackupSnapshot
import com.dayflow.core.backup.Json
import com.dayflow.core.backup.WrongPasswordException
import com.dayflow.core.model.Habit
import com.dayflow.core.stats.HabitStats
import com.dayflow.core.text.Ar
import com.dayflow.core.time.HijriCalendar
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class BackupHabitsTest {

    private val snapshot = BackupSnapshot(
        schemaVersion = 1, appVersion = "1.0.0", createdAtEpochMillis = 1_700_000_000_000,
        tables = mapOf(
            "tasks" to listOf(
                mapOf("id" to 1L, "title" to "مراجعة \"البحث\"\nسطر", "priority" to "HIGH", "startAt" to null, "est" to 1.5, "done" to true),
            ),
            "habits" to emptyList(),
        ),
    )

    @Test fun jsonRoundTrip() {
        val value = mapOf("a" to listOf(1L, -2L, 3.25, "x\u0001y", null, true), "b" to emptyMap<String, Any?>())
        assertEquals(value, Json.parse(Json.write(value)))
    }

    @Test fun exportImportRoundTrip() {
        val json = BackupCodec.toJson(snapshot)
        assertEquals(snapshot, BackupCodec.fromJson(json))
    }

    @Test fun plainBackupRoundTrip() {
        val bytes = BackupCrypto.pack(BackupCodec.toJson(snapshot), null)
        assertFalse(BackupCrypto.isEncrypted(bytes))
        assertEquals(snapshot, BackupCodec.fromJson(BackupCrypto.unpack(bytes, null)))
    }

    @Test fun encryptedBackupRoundTripAndWrongPassword() {
        val bytes = BackupCrypto.pack(BackupCodec.toJson(snapshot), "كلمة-سر".toCharArray())
        assertTrue(BackupCrypto.isEncrypted(bytes))
        assertEquals(snapshot, BackupCodec.fromJson(BackupCrypto.unpack(bytes, "كلمة-سر".toCharArray())))
        try {
            BackupCrypto.unpack(bytes, "خطأ".toCharArray()); fail()
        } catch (e: WrongPasswordException) { /* expected */ }
        // Plaintext must not be visible in the encrypted file.
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("dayflow-backup"))
    }

    @Test fun tamperedFileIsRejected() {
        val bytes = BackupCrypto.pack(BackupCodec.toJson(snapshot), "pw".toCharArray())
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        try {
            BackupCrypto.unpack(bytes, "pw".toCharArray()); fail()
        } catch (e: WrongPasswordException) { /* expected */ }
        try {
            BackupCrypto.unpack("hello".toByteArray(), null); fail()
        } catch (e: BackupFormatException) { /* expected */ }
        try {
            BackupCodec.fromJson("{\"format\":\"other\"}"); fail()
        } catch (e: BackupFormatException) { /* expected */ }
    }

    @Test fun encryptionUsesRandomSalt() {
        val a = BackupCrypto.pack("{}", "pw".toCharArray())
        val b = BackupCrypto.pack("{}", "pw".toCharArray())
        assertFalse(a.contentEquals(b))
        assertArrayEquals(a.copyOfRange(0, 6), b.copyOfRange(0, 6))
    }

    // ---------------- habits ----------------

    private val reading = Habit(1, "القراءة", createdOn = TODAY.minusDays(30))

    @Test fun streakCountsBackFromYesterdayWhenTodayPending() {
        val done = (1L..5L).map { TODAY.minusDays(it) }.toSet()
        assertEquals(5, HabitStats.currentStreak(reading, done, TODAY))
        assertEquals(6, HabitStats.currentStreak(reading, done + TODAY, TODAY))
        assertEquals(0, HabitStats.currentStreak(reading, setOf(TODAY.minusDays(3)), TODAY))
    }

    @Test fun streakSkipsUnscheduledDays() {
        val gym = Habit(2, "الرياضة", days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), createdOn = TODAY.minusDays(30))
        // TODAY is Monday. Done last Wednesday and last Monday.
        val done = setOf(TODAY.minusDays(5), TODAY.minusDays(7))
        assertEquals(2, HabitStats.currentStreak(gym, done, TODAY))
    }

    @Test fun consistencyIgnoresPendingToday() {
        val habit = reading.copy(createdOn = TODAY.minusDays(3))
        val done = setOf(TODAY.minusDays(3), TODAY.minusDays(1))
        assertEquals(2.0 / 3.0, HabitStats.consistency(habit, done, TODAY.minusDays(10), TODAY, TODAY), 1e-9)
        assertEquals(4, HabitStats.bestStreak(habit, done + setOf(TODAY.minusDays(2), TODAY), TODAY))
    }

    // ---------------- formatting & hijri ----------------

    @Test fun arabicFormatting() {
        assertEquals("ساعة و30 دقيقة", Ar.duration(90))
        assertEquals("ساعتان", Ar.duration(120))
        assertEquals("8:05 ص", Ar.time(LocalTime.of(8, 5)))
        assertEquals("12:00 م", Ar.time(LocalTime.NOON))
        assertEquals("مهمتان", Ar.tasks(2))
    }

    @Test fun hijriConversionAndAdjustment() {
        // 1 Ramadan 1447 AH = 18 Feb 2026 in Umm al-Qura.
        val h = HijriCalendar.fromGregorian(LocalDate.of(2026, 2, 18))
        assertNotNull(h)
        assertEquals(1447, h!!.year)
        assertEquals(9, h.month)
        assertEquals(1, h.day)
        assertEquals(2, HijriCalendar.fromGregorian(LocalDate.of(2026, 2, 18), adjustmentDays = 1)!!.day)
    }
}
