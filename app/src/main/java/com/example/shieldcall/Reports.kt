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
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import java.util.Locale
import com.google.firebase.firestore.DocumentSnapshot

data class Review(
    val id: String,
    val type: String,
    val text: String,
    val mine: Boolean,
    val authorName: String = "Anonymous User",
    val likes: Long = 0,
    val flags: Long = 0,
    val liked: Boolean = false,
    val level: Int = 0
)
data class ReviewPage(val reviews: List<Review>, val cursor: DocumentSnapshot?, val more: Boolean)
data class Info(
    val spam: Long,
    val scam: Long,
    val safe: Long,
    val myVote: String?,
    val reviews: List<Review>,
    val myTag: String? = null,
    val tags: Map<String, Long> = emptyMap()
)
data class ContactInfo(val name: String?, val photo: String?, val location: String? = null, val lookupUri: String? = null)

data class Flagged(val tail: String, val rid: String, val type: String, val text: String, val author: String, val flags: Long)

object Reports {
    suspend fun isAdmin(): Boolean = try {
        db.collection("admins").document(uid()).get().await().exists()
    } catch (e: Exception) {
        false
    }

    suspend fun loadFlagged(): List<Flagged> =
        db.collectionGroup("reviews")
            .whereGreaterThan("flags", 0)
            .orderBy("flags", Query.Direction.DESCENDING)
            .limit(30)
            .get().await().documents
            .map {
                Flagged(
                    it.reference.parent.parent?.id.orEmpty(),
                    it.id,
                    it.getString("type").orEmpty(),
                    it.getString("text").orEmpty(),
                    it.getString("authorName") ?: "Anonymous User",
                    it.getLong("flags") ?: 0
                )
            }

    suspend fun adminDelete(tail: String, rid: String) {
        db.collection("reports").document(tail).collection("reviews").document(rid).delete().await()
    }

    suspend fun adminDismiss(tail: String, rid: String) {
        db.collection("reports").document(tail).collection("reviews").document(rid).update("flags", 0).await()
    }

    suspend fun adminBan(tail: String, rid: String) {
        val batch = db.batch()
        batch.set(db.collection("bans").document(rid), mapOf("t" to System.currentTimeMillis()))
        batch.delete(db.collection("reports").document(tail).collection("reviews").document(rid))
        batch.commit().await()
    }
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

    fun firstNumber(text: String, region: String): String? {
        val util = PhoneNumberUtil.getInstance()
        val n = util.findNumbers(text, region).firstOrNull()?.number() ?: return null
        return "${n.countryCode}${n.nationalNumber}"
    }

    fun signedIn() = auth.currentUser?.providerData?.any { it.providerId == "google.com" } == true


    private fun uid(): String = auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")
    fun versionCode(c: Context): Long = c.packageManager.getPackageInfo(c.packageName, 0).longVersionCode

    private fun delta(k: String, new: String?, old: String?): Long =
        (if (new == k) 1L else 0L) - (if (old == k) 1L else 0L)

    suspend fun report(context: Context, tail: String, type: String, previous: String? = null, tag: String? = null, previousTag: String? = null) {
        if (previous == type) return
        val uid = uid()
        val currentTime = System.currentTimeMillis()
        val doc = db.collection("reports").document(tail)
        val userDocRef = db.collection("users").document(uid)
        val usedTag = tag
        val voteData = mutableMapOf<String, Any>("type" to type, "time" to currentTime, "appVersion" to versionCode(context))
        if (usedTag != null) voteData["tag"] = usedTag
        val counters = mutableMapOf<String, Any>(
            "spam" to FieldValue.increment(delta("spam", type, previous)),
            "scam" to FieldValue.increment(delta("scam", type, previous)),
            "safe" to FieldValue.increment(delta("safe", type, previous))
        )
        var oldTag: String? = null
        if (previous != null) {
            val snap = doc.get().await()
            if ((snap.getLong(previous) ?: 0L) < 1L) {
                throw IllegalStateException("This number's totals are out of sync, so the vote can't be changed.")
            }
            oldTag = previousTag?.takeIf { (tagCounts(snap)[it] ?: 0L) > 0L }
        }
        val tagChanges = tagDeltas(usedTag, oldTag)
        if (tagChanges.isNotEmpty()) counters["tags"] = tagChanges
        val batch = db.batch()
        batch.set(doc.collection("votes").document(uid), voteData)
        batch.set(userDocRef.collection("votes").document(tail), mapOf("number" to tail, "type" to type, "time" to currentTime))
        batch.set(doc, counters, SetOptions.merge())
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

    suspend fun removeVote(context: Context, tail: String, previous: String, previousTag: String? = null) {
        val uid = uid()
        val doc = db.collection("reports").document(tail)
        val userRef = db.collection("users").document(uid)
        val hadReview = try {
            doc.collection("reviews").document(uid).get().await().exists()
        } catch (e: Exception) {
            false
        }
        val batch = db.batch()
        batch.delete(doc.collection("votes").document(uid))
        batch.delete(userRef.collection("votes").document(tail))
        batch.delete(doc.collection("reviews").document(uid))
        val counters = mutableMapOf<String, Any>(
            "spam" to FieldValue.increment(delta("spam", null, previous)),
            "scam" to FieldValue.increment(delta("scam", null, previous)),
            "safe" to FieldValue.increment(delta("safe", null, previous))
        )
        val snap = doc.get().await()
        if ((snap.getLong(previous) ?: 0L) < 1L) {
            throw IllegalStateException("This number's totals are out of sync, so the vote can't be removed.")
        }
        val oldTag = previousTag?.takeIf { (tagCounts(snap)[it] ?: 0L) > 0L }
        val tagChanges = tagDeltas(null, oldTag)
        if (tagChanges.isNotEmpty()) counters["tags"] = tagChanges
        batch.set(doc, counters, SetOptions.merge())
        batch.commit().await()
        if (hadReview) Contribution.addReviews(context, -1)

        try {
            val snap = userRef.get().await()
            if (snap.exists()) {
                val kept = (snap.get("identifications") as? List<*>).orEmpty()
                    .filter { (it as? Map<*, *>)?.get("number")?.toString() != tail }
                userRef.update("identifications", kept).await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            val dao = SpamDb.get(context).dao()
            dao.removeMine(tail)
            dao.deleteIdentifications(tail)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun review(context: Context, tail: String, type: String, text: String, anonymous: Boolean = Prefs.anonymousReviews) {
        val name = if (anonymous) "Anonymous User" else (auth.currentUser?.displayName ?: userEmail()?.substringBefore("@") ?: "Verified User")
        val ref = db.collection("reports").document(tail).collection("reviews").document(uid())
        val data = mapOf(
            "type" to type,
            "text" to text,
            "authorName" to name,
            "appVersion" to versionCode(context),
            "time" to FieldValue.serverTimestamp(),
            "level" to Badges.level(SpamDb.get(context).dao().identifiedTotal().toLong())
        )
        if (ref.get().await().exists()) ref.update(data).await()
        else {
            ref.set(data + mapOf("likes" to 0, "flags" to 0)).await()
            Contribution.addReviews(context, 1)
        }
    }

    suspend fun deleteReview(context: Context, tail: String) {
        db.collection("reports").document(tail).collection("reviews").document(uid()).delete().await()
        Contribution.addReviews(context, -1)
    }

    suspend fun like(tail: String, rid: String, on: Boolean) {
        val uid = uid()
        val review = db.collection("reports").document(tail).collection("reviews").document(rid)
        val marker = review.collection("likes").document(uid)
        val key = "${tail}_$rid"
        val batch = db.batch()
        batch.update(review, "likes", FieldValue.increment(if (on) 1 else -1))
        if (on) batch.set(marker, mapOf("t" to System.currentTimeMillis())) else batch.delete(marker)
        batch.set(
            db.collection("users").document(uid),
            mapOf("likedReviews" to if (on) FieldValue.arrayUnion(key) else FieldValue.arrayRemove(key)),
            SetOptions.merge()
        )
        batch.commit().await()
    }

    suspend fun flagReview(tail: String, rid: String) {
        val uid = uid()
        val review = db.collection("reports").document(tail).collection("reviews").document(rid)
        val batch = db.batch()
        batch.update(review, "flags", FieldValue.increment(1))
        batch.set(review.collection("flags").document(uid), mapOf("t" to System.currentTimeMillis()))
        batch.commit().await()
    }

    suspend fun loadReviews(tail: String, sort: String = "new", after: DocumentSnapshot? = null): ReviewPage {
        if (tail.isBlank()) return ReviewPage(emptyList(), null, false)
        val uid = uid()
        val liked = try {
            (db.collection("users").document(uid).get().await().get("likedReviews") as? List<*>)
                .orEmpty().map { it.toString() }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
        val base = db.collection("reports").document(tail).collection("reviews")
        var query: Query = if (sort == "liked") base.orderBy("likes", Query.Direction.DESCENDING)
        else base.orderBy("time", Query.Direction.DESCENDING)
        if (after != null) query = query.startAfter(after)
        val docs = query.limit(10).get().await().documents
        val reviews = docs
            .map {
                Review(
                    it.id,
                    it.getString("type").orEmpty(),
                    it.getString("text").orEmpty(),
                    it.id == uid,
                    it.getString("authorName") ?: "Anonymous User",
                    it.getLong("likes") ?: 0,
                    it.getLong("flags") ?: 0,
                    "${tail}_${it.id}" in liked,(it.getLong("level") ?: 0L).toInt()
                )
            }
            .filter { it.flags < 3 || it.mine }
        return ReviewPage(reviews, docs.lastOrNull(), docs.size == 10)
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun load(tail: String): Info {
        if (tail.isBlank()) return Info(0, 0, 0, null, emptyList())
        val uid = uid()
        val doc = db.collection("reports").document(tail)
        val main = doc.get().await()
        val mine = if (main.exists()) doc.collection("votes").document(uid).get().await() else null
        val tags = (main.get("tags") as? Map<String, Any>).orEmpty().mapValues { (it.value as? Number)?.toLong() ?: 0L }
        return Info(
            main.getLong("spam") ?: 0,
            main.getLong("scam") ?: 0,
            main.getLong("safe") ?: 0,
            mine?.getString("type"),
            emptyList(),
            mine?.getString("tag"),
            tags
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

    fun loadLocation(context: Context, number: String): String? {
        if (number.isBlank()) return null
        val util = PhoneNumberUtil.getInstance()
        val geocoder = PhoneNumberOfflineGeocoder.getInstance()
        val defaultRegion = region(context)
        return try {
            val formattedNum = if (number.startsWith("+")) number else "+$number"
            val parsed = util.parse(formattedNum, defaultRegion)
            val geocoderDesc = geocoder.getDescriptionForNumber(parsed, Locale.getDefault())
            val regionCode = util.getRegionCodeForNumber(parsed) ?: defaultRegion
            val countryName = try {
                Locale.Builder().setRegion(regionCode).build().displayCountry.ifBlank { null }
            } catch (_: Exception) { null }

            if (geocoderDesc.isNotBlank() && countryName != null && !geocoderDesc.equals(countryName, ignoreCase = true)) {
                "$geocoderDesc, $countryName"
            } else if (geocoderDesc.isNotBlank()) {
                geocoderDesc
            } else {
                countryName
            }
        } catch (_: Exception) {
            null
        }
    }

    fun loadContactInfo(context: Context, number: String): ContactInfo {
        val reg = region(context)
        val k = key(number, reg) ?: number
        val queries = listOf(number, "+$number", k)
        var name: String? = null
        var photo: String? = null
        var lookup: String? = null
        for (q in queries) {
            if (q.isBlank()) continue
            try {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(q),
                )
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI, ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY),
                    null,
                    null,
                    null,
                )?.use { c ->
                    if (c.moveToFirst()) {
                        name = c.getString(0)
                        photo = c.getString(1)
                        lookup = ContactsContract.Contacts.getLookupUri(c.getLong(2), c.getString(3))?.toString()
                    }
                }
                if (name != null) break
            } catch (_: Exception) {
            }
        }
        val loc = loadLocation(context, number)
        return ContactInfo(name, photo, loc, lookup)
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
        val userRef = db.collection("users").document(user.uid)

        val likes = try {
            (userRef.get().await().get("likedReviews") as? List<*>).orEmpty().map { it.toString() }
        } catch (e: Exception) {
            emptyList()
        }
        for (k in likes) {
            try {
                like(k.substringBefore("_"), k.substringAfter("_"), false)
            } catch (e: Exception) {
            }
        }

        val votes = try {
            userRef.collection("votes").get().await().documents
        } catch (e: Exception) {
            emptyList()
        }
        for (v in votes) {
            val tail = v.getString("number") ?: v.id
            val type = v.getString("type") ?: continue
            try {
                removeVote(context, tail, type)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            userRef.delete().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val dao = SpamDb.get(context).dao()
            dao.clear()
            dao.clearIdentifications()
            dao.clearHangups()
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().remove("recentLookups").apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return try {
            user.delete().await()
            true
        } catch (e: Exception) {
            auth.signOut()
            false
        }
    }
    private fun tagDeltas(new: String?, old: String?): Map<String, Any> {
        val out = HashMap<String, Any>()
        for (k in setOfNotNull(new, old)) {
            val d = (if (new == k) 1L else 0L) - (if (old == k) 1L else 0L)
            if (d != 0L) out[k] = FieldValue.increment(d)
        }
        return out
    }

    @Suppress("UNCHECKED_CAST")
    private fun tagCounts(snap: DocumentSnapshot): Map<String, Long> =
        (snap.get("tags") as? Map<String, Any>).orEmpty().mapValues { (it.value as? Number)?.toLong() ?: 0L }
}