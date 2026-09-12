package com.example.displayconnect.utils

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

object AppLanguage {
    private val selected = MutableStateFlow("pt-BR")
    val language = selected.asStateFlow()
    fun load(context: Context) {
        selected.value = context.getSharedPreferences("display_language", Context.MODE_PRIVATE)
            .getString("language", "pt-BR").let { if (it == "en") "en" else "pt-BR" }
    }
    fun select(context: Context, tag: String) {
        selected.value = if (tag == "en") "en" else "pt-BR"
        context.getSharedPreferences("display_language", Context.MODE_PRIVATE).edit()
            .putString("language", selected.value).apply()
    }
    fun context(base: Context, tag: String = selected.value): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(config)
    }
}
