package com.myday.app.nlp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ContactMatch(val name: String, val phone: String)

/** البحث عن شخص بالاسم في جهات الاتصال (يتجاهل الهمزات والتشكيل). */
object ContactsLookup {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun find(context: Context, spokenName: String, limit: Int = 5): List<ContactMatch> {
        if (!hasPermission(context) || spokenName.isBlank()) return emptyList()
        val target = ArabicTaskParser.normalize(spokenName).trim()
        val all = mutableListOf<ContactMatch>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val phone = c.getString(1) ?: continue
                    all += ContactMatch(name, phone)
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
        fun score(m: ContactMatch): Int {
            val n = ArabicTaskParser.normalize(m.name).trim()
            return when {
                n == target -> 0
                n.startsWith("$target ") -> 1
                n.split(' ').any { it == target } -> 2
                n.contains(target) -> 3
                else -> 9
            }
        }
        return all.map { it to score(it) }.filter { it.second < 9 }
            .sortedBy { it.second }
            .map { it.first }
            .distinctBy { it.phone.filter { ch -> ch.isDigit() }.takeLast(9) }
            .take(limit)
    }
}
