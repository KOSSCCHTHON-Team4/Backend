package team4.emotionmap.letter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;

/**
 * Parsed GET /letters filters. All supplied axis ranges are ORed; supplied categories are ORed;
 * the axis and category groups are ANDed.
 */
final class LetterFilter {

    private static final Comparator<AxisRange> RANGE_ORDER = Comparator
            .comparingDouble(AxisRange::min)
            .thenComparingDouble(AxisRange::max);

    private final Map<AtmosphereAxis, List<AxisRange>> axisRanges;
    private final Set<PlaceCategoryCode> categories;
    private final boolean supplied;

    private LetterFilter(Map<AtmosphereAxis, List<AxisRange>> axisRanges,
                         Set<PlaceCategoryCode> categories, boolean supplied) {
        EnumMap<AtmosphereAxis, List<AxisRange>> copiedAxes = new EnumMap<>(AtmosphereAxis.class);
        axisRanges.forEach((axis, ranges) -> copiedAxes.put(axis, List.copyOf(ranges)));
        this.axisRanges = Map.copyOf(copiedAxes);
        this.categories = categories.isEmpty() ? Set.of() : Set.copyOf(categories);
        this.supplied = supplied;
    }

    static LetterFilter parse(List<String> atmosphereValues, List<String> categoryValues) {
        List<String> axes = atmosphereValues == null ? List.of() : atmosphereValues;
        List<String> categoryCodes = categoryValues == null ? List.of() : categoryValues;
        boolean supplied = !axes.isEmpty() || !categoryCodes.isEmpty();
        EnumMap<AtmosphereAxis, List<AxisRange>> ranges = new EnumMap<>(AtmosphereAxis.class);
        for (String value : axes) {
            ParsedRange parsed = parseRange(value);
            List<AxisRange> group = ranges.computeIfAbsent(parsed.axis(), ignored -> new ArrayList<>());
            if (!group.contains(parsed.range())) {
                group.add(parsed.range());
            }
        }
        ranges.values().forEach(group -> group.sort(RANGE_ORDER));

        EnumSet<PlaceCategoryCode> categories = EnumSet.noneOf(PlaceCategoryCode.class);
        for (String value : categoryCodes) {
            if (value == null || value.isEmpty()) {
                throw invalid("categories");
            }
            PlaceCategoryCode code = PlaceCategoryCode.fromCode(value)
                    .orElseThrow(() -> invalid("categories"));
            categories.add(code);
        }
        return new LetterFilter(ranges, categories, supplied);
    }

    boolean isFiltered() {
        return supplied;
    }

    List<AxisRange> rangesFor(AtmosphereAxis axis) {
        return axisRanges.getOrDefault(axis, List.of());
    }

    Set<PlaceCategoryCode> categories() {
        return categories;
    }

    /** Stable equivalent-condition representation used only in the signed cursor context. */
    String canonical() {
        StringJoiner axes = new StringJoiner("|");
        for (AtmosphereAxis axis : AtmosphereAxis.ordered()) {
            for (AxisRange range : rangesFor(axis)) {
                axes.add(axis.name() + ":" + Double.toString(range.min()) + "~" + Double.toString(range.max()));
            }
        }
        StringJoiner categoryCodes = new StringJoiner(",");
        for (PlaceCategoryCode category : PlaceCategoryCode.ordered()) {
            if (categories.contains(category)) {
                categoryCodes.add(category.name());
            }
        }
        return "supplied=" + supplied + ";axes=" + axes + ";categories=" + categoryCodes;
    }

    private static ParsedRange parseRange(String value) {
        if (value == null) {
            throw invalid("atmospheres");
        }
        int colon = value.indexOf(':');
        int tilde = value.indexOf('~');
        if (colon < 1 || colon != value.lastIndexOf(':') || tilde <= colon + 1
                || tilde != value.lastIndexOf('~') || tilde == value.length() - 1) {
            throw invalid("atmospheres");
        }
        AtmosphereAxis axis;
        try {
            axis = AtmosphereAxis.valueOf(value.substring(0, colon));
        } catch (IllegalArgumentException error) {
            throw invalid("atmospheres");
        }
        double min = parseBound(value.substring(colon + 1, tilde));
        double max = parseBound(value.substring(tilde + 1));
        if (min > max) {
            throw invalid("atmospheres");
        }
        return new ParsedRange(axis, new AxisRange(min, max));
    }

    /** AtmosphereAxis rejects mathematical overflow before binary64 rounding and normalizes -0. */
    private static double parseBound(String value) {
        try {
            return AtmosphereAxis.parseJsonNumber(value);
        } catch (IllegalArgumentException error) {
            throw invalid("atmospheres");
        }
    }

    private static ContractError invalid(String field) {
        return ContractError.of(ErrorCode.INVALID_REQUEST, FieldError.invalid(field));
    }

    record AxisRange(double min, double max) {
        AxisRange {
            min = min == 0.0 ? 0.0 : min;
            max = max == 0.0 ? 0.0 : max;
        }
    }

    private record ParsedRange(AtmosphereAxis axis, AxisRange range) {
    }
}
