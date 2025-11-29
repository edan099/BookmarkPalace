package com.longlong.bookmark.i18n

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey
import java.util.*

/**
 * 插件资源文件 Bundle
 */
object LongLongBookmarkBundle : DynamicBundle("messages.LongLongBookmarkBundle") {
    
    private var currentLocale: Locale = Locale.SIMPLIFIED_CHINESE
    
    /**
     * 根据 Messages 的设置切换语言
     */
    fun syncWithMessages() {
        currentLocale = if (Messages.isEnglish()) Locale.ENGLISH else Locale.SIMPLIFIED_CHINESE
        // 强制重新加载资源
        clearLocaleCache()
    }
    
    fun getMessage(@PropertyKey(resourceBundle = "messages.LongLongBookmarkBundle") key: String): String {
        return getMessage(key, currentLocale)
    }
    
    private fun getMessage(key: String, locale: Locale): String {
        return try {
            val bundle = ResourceBundle.getBundle("messages.LongLongBookmarkBundle", locale)
            bundle.getString(key)
        } catch (e: Exception) {
            key
        }
    }
}
