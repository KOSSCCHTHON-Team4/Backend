package team4.emotionmap.platform.web.json;

import team4.emotionmap.contracts.dictionary.AnalyzedAtmospheres;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import tools.jackson.databind.module.SimpleModule;

/** contracts 값 객체의 JSON 표현을 한 곳에서 등록한다. 앱 mapper 와 테스트용 mapper 가 같은 모듈을 쓴다. */
public class ContractsJacksonModule extends SimpleModule {

    public ContractsJacksonModule() {
        super("emotionmap-contracts");
        addDeserializer(Atmospheres.class, new AtmospheresJson.Deserializer());
        addSerializer(Atmospheres.class, new AtmospheresJson.Serializer());
        addSerializer(AnalyzedAtmospheres.class, new AtmospheresJson.AnalyzedSerializer());
    }
}
