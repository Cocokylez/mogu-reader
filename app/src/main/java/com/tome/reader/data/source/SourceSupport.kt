package com.tome.reader.data.source

import java.security.MessageDigest

/** Image file extensions the app treats as readable pages. */
object ImageExtensions {
    val SET = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "heif", "jfif", "apng"
    )

    fun isImage(name: String): Boolean {
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return false
        return name.substring(dot + 1).lowercase() in SET
    }
}

/**
 * Compares strings so embedded numbers sort numerically: "page2" precedes
 * "page10". Without this, comic pages come out in lexicographic (wrong) order.
 */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ni = i
                while (ni < a.length && a[ni].isDigit()) ni++
                var nj = j
                while (nj < b.length && b[nj].isDigit()) nj++
                val sa = a.substring(i, ni).trimStart('0').ifEmpty { "0" }
                val sb = b.substring(j, nj).trimStart('0').ifEmpty { "0" }
                if (sa.length != sb.length) return sa.length - sb.length
                val cmp = sa.compareTo(sb)
                if (cmp != 0) return cmp
                i = ni
                j = nj
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}

/** Stable cache keys derived from a uri string. */
object Keys {
    fun forUri(uri: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(uri.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
