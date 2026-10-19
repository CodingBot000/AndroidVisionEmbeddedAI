package dev.robotvision.domain
import org.junit.Assert.*
import org.junit.Test
class CommandParserTest {
    private val parser=ParseVoiceCommand()
    @Test fun cup() { assertEquals(VoiceCommand.Find(Targets.byLabel("cup")!!),parser.parse("컵 찾아줘")) }
    @Test fun bottle() { assertEquals(VoiceCommand.Find(Targets.byLabel("bottle")!!),parser.parse("물병 어디 있어")) }
    @Test fun stop() { assertEquals(VoiceCommand.Stop,parser.parse("검색 중지")) }
    @Test fun unsupported() { assertTrue(parser.parse("모르는 물건 찾아줘") is VoiceCommand.Unsupported) }
    @Test fun punctuationAndParticle() { assertEquals(VoiceCommand.Find(Targets.byLabel("cup")!!),parser.parse("  컵을, 찾아줘! ")) }
    @Test fun retry() { assertEquals(VoiceCommand.Retry,parser.parse("다시 찾아줘")) }
    @Test fun clear() { assertEquals(VoiceCommand.Clear,parser.parse("대상 초기화")) }
    @Test fun phoneAlias() { assertEquals(VoiceCommand.Find(Targets.byLabel("cell phone")!!),parser.parse("핸드폰 찾아줘")) }
    @Test fun unsupportedMonitor() { assertTrue(parser.parse("모니터 찾아줘") is VoiceCommand.Unsupported) }
    @Test fun unknownBlank() { assertTrue(parser.parse(" ") is VoiceCommand.Unknown) }
    @Test fun noSubstringFalsePositive() { assertTrue(parser.parse("책상 찾아줘") is VoiceCommand.Unsupported) }
    @Test fun chairSearch() { assertEquals(VoiceCommand.Find(Targets.byLabel("chair")!!),parser.parse("의자 검색해줘")) }
    @Test fun stopVariants() { for (v in listOf("찾기 그만","중단해")) assertEquals(VoiceCommand.Stop,parser.parse(v)) }
    @Test fun moonshineWordMarker() { assertEquals(VoiceCommand.Find(Targets.byLabel("cup")!!),parser.parse("▁컵 찾아줘")) }
}
