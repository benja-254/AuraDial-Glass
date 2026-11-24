package com.auradial.glassdialer

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

data class ContactItem(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val photoUri: String?
)

object ContactsHelper {

    fun searchContacts(context: Context, query: String): List<ContactItem> {
        val results = mutableListOf<ContactItem>()
        val cleanQuery = query.trim()

        val selection: String?
        val selectionArgs: Array<String>?

        if (cleanQuery.isEmpty()) {
            selection = null
            selectionArgs = null
        } else if (cleanQuery.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }) {
            selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            selectionArgs = arrayOf("%${cleanQuery.replace(" ", "")}%")
        } else {
            selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} LIKE ?"
            selectionArgs = arrayOf("%$cleanQuery%")
        }

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
            ),
            selection,
            selectionArgs,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

            val seenNumbers = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                val id = if (idIdx >= 0) cursor.getString(idIdx) ?: "" else ""
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Unknown" else "Unknown"
                val number = if (numIdx >= 0) cursor.getString(numIdx) ?: "" else ""
                val photo = if (photoIdx >= 0) cursor.getString(photoIdx) else null

                val normalized = number.filter { it.isDigit() }
                if (number.isNotBlank() && seenNumbers.add(normalized)) {
                    results.add(
                        ContactItem(
                            id = id,
                            name = name,
                            phoneNumber = number,
                            photoUri = photo
                        )
                    )
                }
            }
        }
        return results
    }

    fun resolveContactName(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return null
    }
}
