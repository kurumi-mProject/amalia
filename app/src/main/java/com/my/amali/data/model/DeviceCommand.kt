package com.my.amali.data.model

/**
 * Одна команда устройства, извлечённая из JSON-ответа LLM.
 *
 * LLM может вернуть несколько команд за раз (пользователь сказал
 * "включи вайфай и убавь яркость до тридцати процентов").
 *
 * Формат JSON который генерирует модель:
 * ```json
 * {
 *   "reply": "окей",
 *   "commands": [
 *     { "action": "set_wifi", "value": true },
 *     { "action": "set_brightness", "value": 76 }
 *   ]
 * }
 * ```
 */
data class DeviceCommand(
    val action: Action,
    val value: Any? = null,           // Boolean / Int / String зависит от action
) {
    enum class Action(val key: String) {
        SET_WIFI("set_wifi"),                   // value: Boolean
        SET_BLUETOOTH("set_bluetooth"),         // value: Boolean
        SET_BRIGHTNESS("set_brightness"),       // value: Int 0-100 (%)
        SET_VOLUME("set_volume"),               // value: Int 0-100 (%)
        SET_FLASHLIGHT("set_flashlight"),       // value: Boolean
        SET_TIMER("set_timer"),                 // value: Int (секунды)
        SET_ALARM("set_alarm"),                 // value: String "HH:mm"
        OPEN_APP("open_app"),                   // value: String (package or name)
        OPEN_SETTINGS("open_settings"),         // value: String? (section)
        WEB_SEARCH("web_search"),               // value: String (query)
        ;

        companion object {
            fun fromKey(key: String): Action? = entries.firstOrNull { it.key == key }
        }
    }

    companion object {
        /**
         * Парсит список команд из JSONArray строки.
         * Возвращает пустой список если JSON невалидный.
         */
        fun parseList(jsonArrayStr: String): List<DeviceCommand> = runCatching {
            val arr = org.json.JSONArray(jsonArrayStr)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val action = Action.fromKey(obj.optString("action")) ?: return@mapNotNull null
                val value: Any? = obj.opt("value")
                DeviceCommand(action, value)
            }
        }.getOrDefault(emptyList())
    }
}
