package com.example.shoplog.data.repository

import com.example.shoplog.data.local.dao.ShoppingDao
import com.example.shoplog.data.remote.FirebaseSyncManager
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val shoppingDao: ShoppingDao,
    private val firebaseSyncManager: FirebaseSyncManager
) {

    private val auth: FirebaseAuth?
        get() = runCatching { FirebaseAuth.getInstance() }.getOrNull()

    private val _userRefreshTrigger = MutableStateFlow(System.currentTimeMillis())

    fun refreshUser() {
        runCatching { auth?.currentUser?.reload() }
        _userRefreshTrigger.value = System.currentTimeMillis()
    }

    val currentUserState: Flow<FirebaseUser?> = callbackFlow {
        val instance = auth
        if (instance == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = FirebaseAuth.AuthStateListener {
            trySend(instance.currentUser)
        }
        instance.addAuthStateListener(listener)

        val job = launch {
            _userRefreshTrigger.collect {
                trySend(instance.currentUser)
            }
        }

        awaitClose {
            instance.removeAuthStateListener(listener)
            job.cancel()
        }
    }

    val currentUserId: String
        get() = auth?.currentUser?.uid ?: "offline_user"

    val currentEmail: String?
        get() = auth?.currentUser?.email

    val isAnonymous: Boolean
        get() = auth?.currentUser?.isAnonymous ?: true

    suspend fun signInAnonymously(): Result<Unit> {
        val instance = auth ?: return Result.failure(Exception("Firebase Auth not initialized."))
        return try {
            if (instance.currentUser == null) {
                instance.signInAnonymously().await()
            }
            refreshUser()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmailPassword(email: String, pass: String): Result<String> {
        val instance = auth ?: return Result.failure(Exception("Firebase Auth not initialized."))
        val oldUid = currentUserId
        val wasAnonymous = isAnonymous

        return try {
            val credential = EmailAuthProvider.getCredential(email, pass)
            val user = instance.currentUser

            val newUid = if (user != null && user.isAnonymous) {
                try {
                    val authResult = user.linkWithCredential(credential).await()
                    runCatching { authResult.user?.reload()?.await() }
                    authResult.user?.uid ?: instance.currentUser?.uid ?: ""
                } catch (e: FirebaseAuthUserCollisionException) {
                    val authResult = instance.signInWithCredential(credential).await()
                    authResult.user?.uid ?: ""
                }
            } else {
                val authResult = instance.createUserWithEmailAndPassword(email, pass).await()
                authResult.user?.uid ?: ""
            }

            refreshUser()

            if (newUid.isNotBlank()) {
                if (wasAnonymous) {
                    if (oldUid != newUid) {
                        shoppingDao.reassignListOwner(oldUid, newUid)
                    }
                } else if (oldUid != newUid) {
                    shoppingDao.clearAllData()
                }
                runCatching { firebaseSyncManager.fetchAndSyncRemoteUserLists(newUid) }
                runCatching { firebaseSyncManager.syncUnsyncedLists() }
            }

            Result.success(newUid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithEmailPassword(email: String, pass: String): Result<String> {
        val instance = auth ?: return Result.failure(Exception("Firebase Auth not initialized."))
        val oldUid = currentUserId
        val wasAnonymous = isAnonymous

        return try {
            val authResult = instance.signInWithEmailAndPassword(email, pass).await()
            val newUid = authResult.user?.uid ?: ""

            refreshUser()

            if (newUid.isNotBlank()) {
                if (wasAnonymous) {
                    if (oldUid != newUid) {
                        shoppingDao.reassignListOwner(oldUid, newUid)
                    }
                } else if (oldUid != newUid) {
                    shoppingDao.clearAllData()
                }
                runCatching { firebaseSyncManager.fetchAndSyncRemoteUserLists(newUid) }
                runCatching { firebaseSyncManager.syncUnsyncedLists() }
            }

            Result.success(newUid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String): Result<String> {
        val instance = auth ?: return Result.failure(Exception("Firebase Auth not initialized."))
        val oldUid = currentUserId
        val wasAnonymous = isAnonymous

        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val user = instance.currentUser

            val newUid = if (user != null && user.isAnonymous) {
                try {
                    val authResult = user.linkWithCredential(credential).await()
                    runCatching { authResult.user?.reload()?.await() }
                    authResult.user?.uid ?: instance.currentUser?.uid ?: ""
                } catch (e: FirebaseAuthUserCollisionException) {
                    val authResult = instance.signInWithCredential(credential).await()
                    authResult.user?.uid ?: ""
                }
            } else {
                val authResult = instance.signInWithCredential(credential).await()
                authResult.user?.uid ?: ""
            }

            refreshUser()

            if (newUid.isNotBlank()) {
                if (wasAnonymous) {
                    if (oldUid != newUid) {
                        shoppingDao.reassignListOwner(oldUid, newUid)
                    }
                } else if (oldUid != newUid) {
                    shoppingDao.clearAllData()
                }
                runCatching { firebaseSyncManager.fetchAndSyncRemoteUserLists(newUid) }
                runCatching { firebaseSyncManager.syncUnsyncedLists() }
            }

            Result.success(newUid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        val uid = currentUserId
        if (!isAnonymous && uid.isNotBlank() && uid != "offline_user") {
            runCatching { firebaseSyncManager.syncUnsyncedLists() }
        }
        shoppingDao.clearAllData()
        auth?.signOut()
        signInAnonymously()
        refreshUser()
    }
}
