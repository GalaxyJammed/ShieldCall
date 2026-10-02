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

data class Review(
    val type: String,
    val text: String,
    val mine: Boolean,
    val authorName: String = "Anonymous User"
)
data class Info(val spam: Long, val scam: Long, val safe: Long, val myVote: String?, val reviews: List<Review>)
data class ContactInfo(val name: String?, val photo: String?)

data class UserAnalytics(
    val profileViews: Long = 0,
    val profileSearches: Long = 0,
    val viewCountries: Map<String, Long> = emptyMap(),
    val searchCountries: Map<String, Long> = emptyMap()
)

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


    private fun uid(): String = auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")

    suspend fun report(context: Context, tail: String, type: String) {
        val uid = uid()
        val currentTime = System.currentTimeMillis()
        val doc = db.collection("reports").document(tail)
        val userDocRef = db.collection("users").document(uid)
        val batch = db.batch()
        batch.set(doc.collection("votes").document(uid), mapOf("type" to type, "time" to currentTime))
        batch.set(userDocRef.collection("votes").document(tail), mapOf("number" to tail, "type" to type, "time" to currentTime))
        batch.set(
            doc,
            mapOf(
                "spam" to FieldValue.increment(if (type == "spam") 1 else 0),
                "scam" to FieldValue.increment(if (type == "scam") 1 else 0),
                "safe" to FieldValue.increment(if (type == "safe") 1 else 0)
            ),
            SetOptions.merge()
        )
        batch.commit().await()

        try {
            val dao = SpamDb.get(context).dao()
            dao.insert(SpamNumber(tail, type))
            dao.deleteIdentifications(tail)
            dao.addIdentification(IdentificationEntry(number = tail, type = type, time = currentTime))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            syncUserStats(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun review(tail: String, type: String, text: String, anonymous: Boolean = Prefs.anonymousReviews) {
        val name = if (anonymous) "Anonymous User" else (auth.currentUser?.displayName ?: userEmail()?.substringBefore("@") ?: "Verified User")
        db.collection("reports").document(tail).collection("reviews").document(uid())
            .set(mapOf(
                "type" to type,
                "text" to text,
                "authorName" to name,
                "time" to FieldValue.serverTimestamp()
            ))
            .await()
    }

    suspend fun load(tail: String): Info {
        if (tail.isBlank()) return Info(0, 0, 0, null, emptyList())
        return try {
            val uid = uid()
            val doc = db.collection("reports").document(tail)
            val main = doc.get().await()
            val mine = if (main.exists()) {
                try {
                    doc.collection("votes").document(uid).get().await().getString("type")
                } catch (_: Exception) {
                    null
                }
            } else null
            Info(
                if (main.exists()) main.getLong("spam") ?: 0 else 0,
                if (main.exists()) main.getLong("scam") ?: 0 else 0,
                if (main.exists()) main.getLong("safe") ?: 0 else 0,
                mine,
                emptyList()
            )
        } catch (e: Exception) {
            Info(0, 0, 0, null, emptyList())
        }
    }

    suspend fun loadReviews(tail: String): List<Review> {
        if (tail.isBlank()) return emptyList()
        return try {
            val uid = uid()
            db.collection("reports").document(tail).collection("reviews")
                .orderBy("time", Query.Direction.DESCENDING)
                .limit(10)
                .get().await().documents
                .map {
                    Review(
                        it.getString("type").orEmpty(),
                        it.getString("text").orEmpty(),
                        it.id == uid,
                        it.getString("authorName") ?: "Anonymous User"
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun loadUserAnalytics(): UserAnalytics {
        val uid = auth.currentUser?.uid ?: return UserAnalytics()
        return try {
            val doc = db.collection("users").document(uid).get().await()
            if (doc.exists()) {
                val views = doc.getLong("profileViews") ?: 0L
                val searches = doc.getLong("profileSearches") ?: 0L
                val rawViews = doc.get("viewCountries") as? Map<String, Any> ?: emptyMap()
                val viewCountries = rawViews.mapValues { (it.value as? Number)?.toLong() ?: 0L }
                val rawSearches = doc.get("searchCountries") as? Map<String, Any> ?: emptyMap()
                val searchCountries = rawSearches.mapValues { (it.value as? Number)?.toLong() ?: 0L }
                UserAnalytics(views, searches, viewCountries, searchCountries)
            } else {
                UserAnalytics()
            }
        } catch (e: Exception) {
            UserAnalytics()
        }
    }

    fun recordProfileView(context: Context) {
        val uid = auth.currentUser?.uid ?: return
        val countryName = Locale.Builder().setRegion(region(context)).build().displayCountry.ifBlank { "Global" }
        db.collection("users").document(uid).set(
            mapOf(
                "profileViews" to FieldValue.increment(1),
                "viewCountries" to mapOf(countryName to FieldValue.increment(1))
            ),
            SetOptions.merge()
        )
    }

    fun recordProfileSearch(context: Context) {
        val uid = auth.currentUser?.uid ?: return
        val countryName = Locale.Builder().setRegion(region(context)).build().displayCountry.ifBlank { "Global" }
        db.collection("users").document(uid).set(
            mapOf(
                "profileSearches" to FieldValue.increment(1),
                "searchCountries" to mapOf(countryName to FieldValue.increment(1))
            ),
            SetOptions.merge()
        )
    }

    fun lookup(tail: String, onResult: (String) -> Unit) {
        if (!signedIn()) return
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
            }
        }
        return ContactInfo(null, null)
    }

    fun loadContactName(context: Context, number: String): String? {
        return loadContactInfo(context, number).name
    }

    fun userEmail(): String? = auth.currentUser?.email
    fun userPhotoUrl(): String? = auth.currentUser?.photoUrl?.toString()
    fun userDisplayName(): String? = auth.currentUser?.displayName

    @Suppress("UNCHECKED_CAST")
    suspend fun syncUserStats(context: Context): Boolean {
        val user = auth.currentUser ?: return false
        val uid = user.uid
        val dao = SpamDb.get(context).dao()

        return try {
            val userDocRef = db.collection("users").document(uid)

            val userVotesList = mutableListOf<IdentificationEntry>()
            try {
                val votesSnapshot = userDocRef.collection("votes").get().await()
                for (doc in votesSnapshot.documents) {
                    val num = doc.getString("number") ?: doc.id
                    val type = doc.getString("type") ?: ""
                    val time = doc.getLong("time") ?: System.currentTimeMillis()
                    if (num.isNotBlank() && type.isNotBlank()) {
                        userVotesList.add(IdentificationEntry(number = num, type = type, time = time))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val remoteDoc = try {
                userDocRef.get().await()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            val remoteIdentifications = mutableListOf<IdentificationEntry>()
            val remoteHangups = mutableListOf<HangupEntry>()

            if (remoteDoc != null && remoteDoc.exists()) {
                val rawIdentList = remoteDoc.get("identifications") as? List<*> ?: emptyList<Any>()
                rawIdentList.forEach { item ->
                    if (item is Map<*, *>) {
                        val num = item["number"]?.toString().orEmpty()
                        val type = item["type"]?.toString().orEmpty()
                        val time = (item["time"] as? Number)?.toLong() ?: 0L
                        if (num.isNotBlank() && type.isNotBlank()) {
                            remoteIdentifications.add(IdentificationEntry(number = num, type = type, time = time))
                        }
                    }
                }

                val rawHangupList = remoteDoc.get("hangups") as? List<*> ?: emptyList<Any>()
                rawHangupList.forEach { item ->
                    if (item is Map<*, *>) {
                        val num = item["number"]?.toString().orEmpty()
                        val time = (item["time"] as? Number)?.toLong() ?: 0L
                        val saved = (item["secondsSaved"] as? Number)?.toInt() ?: 15
                        if (num.isNotBlank()) {
                            remoteHangups.add(HangupEntry(number = num, time = time, secondsSaved = saved))
                        }
                    }
                }
            }

            val localIdentifications = dao.getAllIdentifications()
            val localHangups = dao.getAllHangups()

            val mergedIdentificationsMap = mutableMapOf<String, IdentificationEntry>()
            (localIdentifications + remoteIdentifications + userVotesList)
                .sortedBy { it.time }
                .forEach { item ->
                    mergedIdentificationsMap[item.number] = item
                }
            val mergedIdentifications = mergedIdentificationsMap.values.toList()

            val mergedHangupsMap = mutableMapOf<String, HangupEntry>()
            (localHangups + remoteHangups).forEach { item ->
                val key = "${item.number}_${item.time}"
                mergedHangupsMap[key] = item
            }
            val mergedHangups = mergedHangupsMap.values.toList()

            try {
                dao.clearIdentifications()
                if (mergedIdentifications.isNotEmpty()) {
                    dao.addIdentifications(mergedIdentifications.map { it.copy(id = 0) })
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                dao.clearHangups()
                if (mergedHangups.isNotEmpty()) {
                    dao.addHangups(mergedHangups.map { it.copy(id = 0) })
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                val firestoreData = mapOf(
                    "identifications" to mergedIdentifications.map {
                        mapOf("number" to it.number, "type" to it.type, "time" to it.time)
                    },
                    "hangups" to mergedHangups.map {
                        mapOf("number" to it.number, "time" to it.time, "secondsSaved" to it.secondsSaved)
                    },
                    "email" to (userEmail() ?: ""),
                    "lastSynced" to FieldValue.serverTimestamp()
                )
                userDocRef.set(firestoreData, SetOptions.merge()).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteAccountAndData(context: Context): Boolean {
        val user = auth.currentUser ?: return false
        val uid = user.uid

        return try {
            val dao = SpamDb.get(context).dao()

            try {
                val userVotesSnapshot = db.collection("users").document(uid).collection("votes").get().await()
                for (voteDoc in userVotesSnapshot.documents) {
                    val number = voteDoc.getString("number") ?: voteDoc.id
                    val type = voteDoc.getString("type")
                    val reportRef = db.collection("reports").document(number)

                    try {
                        reportRef.collection("votes").document(uid).delete().await()
                    } catch (_: Exception) {}

                    try {
                        reportRef.collection("reviews").document(uid).delete().await()
                    } catch (_: Exception) {}

                    if (!type.isNullOrBlank()) {
                        try {
                            reportRef.set(
                                mapOf(type to FieldValue.increment(-1)),
                                SetOptions.merge()
                            ).await()
                        } catch (_: Exception) {}
                    }

                    try {
                        voteDoc.reference.delete().await()
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
            } catch (_: Exception) {}

            try {
                db.collection("users").document(uid).delete().await()
            } catch (_: Exception) {}

            try {
                dao.clear()
                dao.clearIdentifications()
                dao.clearHangups()
                context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .edit()
                    .remove("recentLookups")
                    .apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                user.delete().await()
            } catch (_: Exception) {
                auth.signOut()
            }

            auth.signInAnonymously().await()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}