package com.s3.providerandriod6
import android.content.Context
import java.io.File
class SetManager(private val context: Context) {

    private val settingsFile = File(logDirectory2(), "set.log")
    private fun logDirectory2(): File? {
        return context.getExternalFilesDir("logs")?.also { dir ->
            if (!dir.exists()) dir.mkdirs()
        }
    }
    // Сохранить одну настройку
    fun put(key: String, value: String) {
        val settings = getAll().toMutableMap()
        settings[key] = value
        saveAll(settings)
    }

    // Получить настройку
    fun get(key: String, defaultValue: String = ""): String {
        return getAll()[key] ?: defaultValue
    }

    // Удобные методы для boolean
    fun putBoolean(key: String, value: Boolean) {
        put(key, value.toString())
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return get(key, defaultValue.toString()).toBoolean()
    }

    // Удобные методы для int
    fun putInt(key: String, value: Int) {
        put(key, value.toString())
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return get(key, defaultValue.toString()).toIntOrNull() ?: defaultValue
    }

    // Получить все настройки
    fun getAll(): Map<String, String> {
        if (!settingsFile.exists()) return emptyMap()

        return try {
            settingsFile.readLines()
                .filter { it.contains("=") }
                .associate { line ->
                    val parts = line.split("=", limit = 2)
                    parts[0].trim() to parts[1].trim()
                }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // Сохранить все настройки
    fun saveAll(settings: Map<String, String>) {
        try {
            val content = settings.map { (key, value) -> "$key=$value" }.joinToString("\n")
            settingsFile.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Очистить все настройки
    fun clear() {
        settingsFile.delete()
    }

    // Удалить конкретную настройку
    fun remove(key: String) {
        val settings = getAll().toMutableMap()
        settings.remove(key)
        saveAll(settings)
    }

    // Для отладки - показать путь к файлу
    fun getPath(): String = settingsFile.absolutePath

    // Проверить существует ли файл
    fun exists(): Boolean = settingsFile.exists()
}