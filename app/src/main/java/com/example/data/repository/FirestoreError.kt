package com.example.data.repository

import com.google.firebase.firestore.FirebaseFirestoreException

sealed class FirestoreError(val message: String, val cause: Throwable? = null) {
    class PermissionDenied(cause: Throwable? = null) :
        FirestoreError("Permission denied. Please sign in with the authorized Google account.", cause)

    class Unauthenticated(cause: Throwable? = null) :
        FirestoreError("Please sign in with Google to sync your shop data.", cause)

    class NetworkUnavailable(cause: Throwable? = null) :
        FirestoreError("You appear to be offline. Changes will sync when connection returns.", cause)

    class NotFound(cause: Throwable? = null) :
        FirestoreError("Requested item or shared board could not be found.", cause)

    class Unknown(detail: String, cause: Throwable? = null) :
        FirestoreError(detail.ifBlank { "An unexpected cloud sync error occurred." }, cause)

    companion object {
        fun fromThrowable(throwable: Throwable): FirestoreError {
            val fsException = unwrapFirestoreException(throwable)
            return when (fsException?.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> PermissionDenied(throwable)
                FirebaseFirestoreException.Code.UNAUTHENTICATED -> Unauthenticated(throwable)
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> NetworkUnavailable(throwable)
                FirebaseFirestoreException.Code.NOT_FOUND -> NotFound(throwable)
                else -> Unknown(fsException?.localizedMessage ?: throwable.localizedMessage ?: "", throwable)
            }
        }

        private fun unwrapFirestoreException(throwable: Throwable): FirebaseFirestoreException? {
            var current: Throwable? = throwable
            while (current != null) {
                if (current is FirebaseFirestoreException) return current
                current = current.cause
            }
            return null
        }
    }
}
