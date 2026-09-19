package team4.emotionmap.catalog.dto;

import java.util.List;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;

/** API_SPEC 8.3 {@code AtmosphereAxesResponse}. 상수 사전에서 생성하며 DB 를 읽지 않는다. */
public record AtmosphereAxesResponse(int version, List<Axis> items) {

    public record Axis(String code, int order, List<Option> options) {
    }

    public record Option(int value, String label) {
    }

    public static AtmosphereAxesResponse v1() {
        List<Axis> items = AtmosphereAxis.ordered().stream()
                .map(axis -> new Axis(axis.name(), axis.order(), List.of(
                        new Option(AtmosphereAxis.NEGATIVE, axis.negativeLabel()),
                        new Option(AtmosphereAxis.POSITIVE, axis.positiveLabel()))))
                .toList();
        return new AtmosphereAxesResponse(AtmosphereAxis.DEFINITION_VERSION, items);
    }
}
