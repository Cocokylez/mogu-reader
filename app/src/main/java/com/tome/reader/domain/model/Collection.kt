package com.tome.reader.domain.model

/** A user-defined grouping of books (doubles as tagging). */
data class Collection(
    val id: Long,
    val name: String,
    val bookCount: Int = 0,
)
