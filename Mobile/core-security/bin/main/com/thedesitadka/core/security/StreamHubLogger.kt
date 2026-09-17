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

    enum class Category {
        PROVIDER, PLAYER, DOWNLOAD, NETWORK, CONFIG, SECURITY, NAVIGATION, SYSTEM
    }

    fun log(category: Category, level: String, message: String) {
        if (level == "DEBUG" && !isDebugEnabled) return
        println("[$level][TheDesiTadka][${category.name}] ${redact(message)}")
    }

    fun d(tag: String, message: String) {
        if (isDebugEnabled) {
            println("[DEBUG][TheDesiTadka][$tag] ${redact(message)}")
        }
    }

    fun i(tag: String, message: String) {
        println("[INFO][TheDesiTadka][$tag] ${redact(message)}")
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        println("[WARN][TheDesiTadka][$tag] ${redact(message)}")
        throwable?.let { println("[WARN][TheDesiTadka][$tag] Exception: ${it.message}") }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        System.err.println("[ERROR][TheDesiTadka][$tag] ${redact(message)}")
        throwable?.printStackTrace()
    }
}
