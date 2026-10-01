package com.example.shieldcall

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

object Auth {
    suspend fun signIn(context: Context): Boolean {
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(context.getString(R.string.default_web_client_id))
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = CredentialManager.create(context).getCredential(context, request)
            val token = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
            val credential = GoogleAuthProvider.getCredential(token, null)

            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser

            if (currentUser != null && currentUser.isAnonymous) {
                try {
                    currentUser.linkWithCredential(credential).await()
                } catch (e: FirebaseAuthUserCollisionException) {
                    auth.signInWithCredential(credential).await()
                } catch (e: Exception) {
                    auth.signInWithCredential(credential).await()
                }
            } else {
                auth.signInWithCredential(credential).await()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}