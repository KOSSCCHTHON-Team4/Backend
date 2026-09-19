package team4.emotionmap.contracts.memory;

import java.util.Objects;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;

/** {@code memory_categories} 한 행. slot_no 는 저장 슬롯(1~3)이며 관련도 순위가 아니다. */
public record CategoryAssignment(PlaceCategoryCode code, int slotNo, AxisSource source) {

    public CategoryAssignment {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(source, "source");
        if (slotNo < 1 || slotNo > PlaceCategoryCode.MAX_PER_MEMORY) {
            throw new IllegalArgumentException("slotNo must be 1.." + PlaceCategoryCode.MAX_PER_MEMORY);
        }
    }
}
