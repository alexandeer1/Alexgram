package com.exteragram.messenger.icons

sealed class IconPackStorageResult<out T> {
    data class Success<out T>(val value: T) : IconPackStorageResult<T>()
    data class Failure(val error: IconPackStorageError) : IconPackStorageResult<Nothing>()
}
