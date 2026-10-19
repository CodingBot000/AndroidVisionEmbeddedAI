package dev.robotvision.domain

import java.util.Locale

class ParseVoiceCommand {
    fun parse(raw: String): VoiceCommand {
        val text = raw.lowercase(Locale.ROOT).replace("▁", " ").replace(Regex("[\\s\\p{P}]"), "")
        if (text in listOf("검색중지", "검색정지", "찾기중지", "멈춰", "중지", "그만", "찾기그만", "중단해")) return VoiceCommand.Stop
        if (text in listOf("다시찾기", "다시찾아줘", "다시검색", "재검색")) return VoiceCommand.Retry
        if (text in listOf("대상초기화", "초기화", "대상지워")) return VoiceCommand.Clear
        val target = Targets.all.firstOrNull { t -> t.aliases.any { alias ->
            val a = alias.replace(" ", "")
            text == a || listOf("찾아줘", "찾아", "찾기", "어디있어", "어딨어", "검색", "보여줘", "검색해줘").any { verb ->
                text == a + verb || listOf("을", "를", "이", "가", "좀").any { text == a + it + verb }
            }
        } }
        return target?.let { VoiceCommand.Find(it) }
            ?: if (listOf("찾", "어디", "검색").any(text::contains)) VoiceCommand.Unsupported(raw)
            else VoiceCommand.Unknown(raw)
    }
}
