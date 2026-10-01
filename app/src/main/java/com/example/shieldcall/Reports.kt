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
        val userVoteDoc = db.collection("users").document(uid).collection("votes").document(tail)
        val batch = db.batch()
        batch.set(doc.collection("votes").document(uid), mapOf("type" to type))
        batch.set(
            userVoteDoc,
            mapOf("type" to type, "number" to tail, "time" to System.currentTimeMillis())
        )
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
            .map {
                Review(
                    it.getString("type").orEmpty(),
                    it.getString("text").orEmpty(),
                    it.id == uid,
                    it.getString("authorName") ?: "Anonymous User"
                )
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
                "viewCountries.$countryName" to FieldValue.increment(1)
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
                "searchCountries.$countryName" to FieldValue.increment(1)
            ),
            SetOptions.merge()
        )
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

            // 1. Read votes saved under users/{uid}/votes (only reads documents for this user)
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

            // 2. Fetch main user document
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

            // 3. Get local Room entries
            val localIdentifications = dao.getAllIdentifications()
            val localHangups = dao.getAllHangups()

            // 4. Merge all sources
            val mergedIdentificationsMap = mutableMapOf<String, IdentificationEntry>()
            (localIdentifications + remoteIdentifications + userVotesList).forEach { item ->
                val key = "${item.number}_${item.type}_${item.time}"
                mergedIdentificationsMap[key] = item
            }
            val mergedIdentifications = mergedIdentificationsMap.values.toList()

            val mergedHangupsMap = mutableMapOf<String, HangupEntry>()
            (localHangups + remoteHangups).forEach { item ->
                val key = "${item.number}_${item.time}"
                mergedHangupsMap[key] = item
            }
            val mergedHangups = mergedHangupsMap.values.toList()

            // 5. Update local Room database
            if (mergedIdentifications.isNotEmpty()) {
                try {
                    dao.addIdentifications(mergedIdentifications.map { it.copy(id = 0) })
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (mergedHangups.isNotEmpty()) {
                try {
                    dao.addHangups(mergedHangups.map { it.copy(id = 0) })
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 6. Write merged data back to Firestore
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
            try {
                db.collection("users").document(uid).delete().await()
            } catch (_: Exception) {}

            try {
                val reportsDocs = db.collection("reports").get().await().documents
                for (reportDoc in reportsDocs) {
                    try {
                        val voteDoc = reportDoc.reference.collection("votes").document(uid).get().await()
                        if (voteDoc.exists()) {
                            val voteType = voteDoc.getString("type")
                            voteDoc.reference.delete().await()
                            if (voteType != null) {
                                reportDoc.reference.set(
                                    mapOf(voteType to FieldValue.increment(-1)),
                                    SetOptions.merge()
                                ).await()
                            }
                        }

                        val reviewDoc = reportDoc.reference.collection("reviews").document(uid).get().await()
                        if (reviewDoc.exists()) {
                            reviewDoc.reference.delete().await()
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            val dao = SpamDb.get(context).dao()
            dao.clearIdentifications()
            dao.clearHangups()

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