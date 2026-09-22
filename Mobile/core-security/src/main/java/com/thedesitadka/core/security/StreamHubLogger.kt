package com.thedesitadka.core.security

import java.util.regex.Pattern

object StreamHubLogger {

    private var isDebugEnabled: Boolean = true

    fun setDebug(enabled: Boolean) {
        isDebugEnabled = enabled
    }

    private val SENSITIVE_PATTERNS = listOf(
        Pattern.compile("(?i)(authorization\\s*:\\s*bearer\\s+)[a-zA-Z0-9_\\-\\.]+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(cookie\\s*:\\s*)[^\\r\\n]+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(set-cookie\\s*:\\s*)[^\\r\\n]+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(password|passwd|api_key|apikey|secret|token|sig|signature)=([^&\\s]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(private_key|privatekey)[\"']?\\s*:\\s*[\"'][^\"']+[\"']", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(X-Amz-Signature|X-Amz-Credential)=([^&\\s]+)", Pattern.CASE_INSENSITIVE)
    )

    fun redact(message: String): String {
        var sanitized = message
        for (pattern in SENSITIVE_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("$1[REDACTED]")
        }
        return sanitized
    }

    @Volatile
    var logDelegate: ((priority: Int, tag: String, message: String, tr: Throwable?) -> Unit)? = null

    enum class Category {
        PROVIDER, PLAYER, DOWNLOAD, NETWORK, CONFIG, SECURITY, NAVIGATION, SYSTEM
    }

    fun log(category: Category, level: String, message: String) {
        if (level == "DEBUG" && !isDebugEnabled) return
        val r = redact(message)
        println("[$level][TheDesiTadka][${category.name}] $r")
        val priority = when (level.uppercase()) {
            "VERBOSE" -> 2
            "DEBUG" -> 3
            "INFO" -> 4
            "WARN" -> 5
            "ERROR" -> 6
            else -> 4
        }
        logDelegate?.invoke(priority, category.name, r, null)
    }

    fun d(tag: String, message: String) {
        if (isDebugEnabled) {
            val r = redact(message)
            println("[DEBUG][TheDesiTadka][$tag] $r")
            logDelegate?.invoke(3, tag, r, null)
        }
    }

    fun i(tag: String, message: String) {
        val r = redact(message)
        println("[INFO][TheDesiTadka][$tag] $r")
        logDelegate?.invoke(4, tag, r, null)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val r = redact(message)
        println("[WARN][TheDesiTadka][$tag] $r")
        throwable?.let { println("[WARN][TheDesiTadka][$tag] Exception: ${it.message}") }
        logDelegate?.invoke(5, tag, r, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val r = redact(message)
        System.err.println("[ERROR][TheDesiTadka][$tag] $r")
        throwable?.printStackTrace()
        logDelegate?.invoke(6, tag, r, throwable)
    }
}
