package com.example.shieldcall

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

data class Review(val type: String, val text: String, val mine: Boolean)
data class Info(val spam: Long, val scam: Long, val safe: Long, val myVote: String?, val reviews: List<Review>)
data class ContactInfo(val name: String?, val photo: String?)

object Reports {
    private const val THRESHOLD = 3
    private val db get() = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    fun region(c: Context): String =
        c.getSystemService(TelephonyManager::class.java).simCountryIso.uppercase().ifEmpty { Locale.getDefault().country }

    fun key(number: String, region: String): String? {
        val util = PhoneNumberUtil.getInstance()
        return try {
            val p = util.parse(number, region)
            if (util.isValidNumber(p)) "${p.countryCode}${p.nationalNumber}" else null
        } catch (e: NumberParseException) {
            null
        }
    }

    fun signedIn() = auth.currentUser?.providerData?.any { it.providerId == "google.com" } == true

    fun signIn() {
        if (auth.currentUser == null) auth.signInAnonymously()
    }

    private suspend fun uid(): String {
        if (auth.currentUser == null) auth.signInAnonymously().await()
        return auth.currentUser!!.uid
    }

    fun report(tail: String, type: String): Task<Void> {
        val uid = auth.currentUser?.uid ?: return Tasks.forException(IllegalStateException())
        val doc = db.collection("reports").document(tail)
        val batch = db.batch()
        batch.set(doc.collection("votes").document(uid), mapOf("type" to type))
        batch.set(
            doc,
            mapOf(
                "spam" to FieldValue.increment(if (type == "spam") 1 else 0),
                "scam" to FieldValue.increment(if (type == "scam") 1 else 0),
                "safe" to FieldValue.increment(if (type == "safe") 1 else 0)
            ),
            SetOptions.merge()
        )
        return batch.commit()
    }

    suspend fun review(tail: String, type: String, text: String) {
        db.collection("reports").document(tail).collection("reviews").document(uid())
            .set(mapOf("type" to type, "text" to text, "time" to FieldValue.serverTimestamp()))
            .await()
    }

    suspend fun load(tail: String): Info {
        val uid = uid()
        val doc = db.collection("reports").document(tail)
        val main = doc.get().await()
        val mine = doc.collection("votes").document(uid).get().await().getString("type")
        return Info(
            main.getLong("spam") ?: 0,
            main.getLong("scam") ?: 0,
            main.getLong("safe") ?: 0,
            mine,
            emptyList()
        )
    }

    suspend fun loadReviews(tail: String): List<Review> {
        val uid = uid()
        return db.collection("reports").document(tail).collection("reviews")
            .orderBy("time", Query.Direction.DESCENDING)
            .limit(10)
            .get().await().documents
            .map { Review(it.getString("type").orEmpty(), it.getString("text").orEmpty(), it.id == uid) }
    }

    fun lookup(tail: String, onResult: (String) -> Unit) {
        db.collection("reports").document(tail).get().addOnSuccessListener { doc ->
            val spam = doc.getLong("spam") ?: 0
            val scam = doc.getLong("scam") ?: 0
            val safe = doc.getLong("safe") ?: 0
            if (spam + scam >= THRESHOLD && spam + scam > safe) onResult(if (scam > spam) "scam" else "spam")
        }
    }

    fun loadContactInfo(context: Context, number: String): ContactInfo {
        val reg = region(context)
        val k = key(number, reg) ?: number
        val queries = listOf(number, "+$number", k)
        for (q in queries) {
            if (q.isBlank()) continue
            try {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(q),
                )
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                    null,
                    null,
                    null,
                )?.use { c ->
                    if (c.moveToFirst()) {
                        return ContactInfo(c.getString(0), c.getString(1))
                    }
                }
            } catch (_: Exception) {
                // ignore
            }
        }
        return ContactInfo(null, null)
    }

    fun loadContactName(context: Context, number: String): String? {
        return loadContactInfo(context, number).name
    }
}