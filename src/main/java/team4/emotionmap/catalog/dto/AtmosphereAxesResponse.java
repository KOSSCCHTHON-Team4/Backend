package team4.emotionmap.catalog.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;

/** 현재 공개 분위기 4축 사전. 상수 사전에서 생성하며 DB 를 읽지 않는다. */
@Schema(description = "현재 공개 분위기 4축 사전.")
public record AtmosphereAxesResponse(
        @Schema(description = "현재 축 정의 버전.", example = "2") int version,
        List<Axis> items
) {

    @Schema(description = "분위기 축 하나의 연속값 범위와 endpoint 라벨 anchor.")
    public record Axis(
            @Schema(description = "축 코드.", example = "CROWD_LEVEL") String code,
            @Schema(description = "표시 순서.", example = "1") int order,
            @Schema(description = "연속 축 값의 허용 하한.", type = "number", format = "double",
                    example = "-1.0") double minimum,
            @Schema(description = "연속 축 값의 허용 상한.", type = "number", format = "double",
                    example = "1.0") double maximum,
            @Schema(description = "기존 -1/+1 endpoint의 라벨 anchor 두 개다. "
                    + "전체 허용값 목록이 아니며 0과 소수를 배제하지 않는다.") List<Option> options
    ) {
    }

    @Schema(description = "endpoint 라벨 anchor 하나.")
    public record Option(
            @Schema(description = "라벨이 있는 -1 또는 +1 endpoint selector.", type = "integer",
                    format = "int32", example = "-1") int value,
            @Schema(description = "해당 endpoint의 기존 표시 라벨.", example = "조용한") String label
    ) {
    }

    public static AtmosphereAxesResponse current() {
        List<Axis> items = AtmosphereAxis.ordered().stream()
                .map(axis -> new Axis(axis.name(), axis.order(), -1.0, 1.0, List.of(
                        new Option(AtmosphereAxis.NEGATIVE, axis.negativeLabel()),
                        new Option(AtmosphereAxis.POSITIVE, axis.positiveLabel()))))
                .toList();
        return new AtmosphereAxesResponse(AtmosphereAxis.DEFINITION_VERSION, items);
    }
}
