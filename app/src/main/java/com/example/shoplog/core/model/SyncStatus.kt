package com.example.shoplog.core.model

/**
 * Enumeration representing the offline synchronization state of Room entities with Firebase.
 */
enum class SyncStatus {
    SYNCED,
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE
}
