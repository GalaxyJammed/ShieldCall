package com.example.shieldcall

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
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
            FirebaseAuth.getInstance()
                .signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }
}