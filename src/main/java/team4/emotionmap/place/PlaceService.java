package team4.emotionmap.place;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team4.emotionmap.contracts.config.MatchingProperties;
import team4.emotionmap.contracts.config.PaginationProperties;
import team4.emotionmap.contracts.config.ServiceConfigSource;
import team4.emotionmap.contracts.config.ServiceLimits;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;
import team4.emotionmap.contracts.page.PageInfo;
import team4.emotionmap.contracts.signing.ContentHash;
import team4.emotionmap.contracts.signing.SignedClaims;
import team4.emotionmap.contracts.signing.SignedValueCodec;
import team4.emotionmap.contracts.signing.SignedValuePurpose;
import team4.emotionmap.place.dto.PlacePageResponse;
import team4.emotionmap.place.dto.PlaceResponse;

@Service
@RequiredArgsConstructor
public class PlaceService {

    private static final int CURSOR_VERSION = 1;
    private static final int NO_REQUESTED_LIMIT = -1;
    private static final String CLAIM_CONTEXT = "ctx";
    private static final String CLAIM_AFTER = "after";
    private static final String CLAIM_UPPER = "upper";
    private static final Set<String> CURSOR_CLAIMS = Set.of(CLAIM_CONTEXT, CLAIM_AFTER, CLAIM_UPPER);
    private static final Pattern BBOX_COMPONENT = Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?");
    private static final Pattern LIMIT = Pattern.compile("[0-9]+");
    private static final Pattern CANONICAL_UUID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final PlaceRepository placeRepository;
    private final ServiceConfigSource serviceConfigSource;
    private final PaginationProperties paginationProperties;
    private final SignedValueCodec signedValueCodec;
    private final Clock clock;
    private final MatchingProperties matchingProperties;

    @Transactional(readOnly = true)
    public PlacePageResponse findInBoundingBox(UUID userId, String bbox, String cursor, String limit) {
        BoundingBox box = parseBoundingBox(bbox);
        int requestedLimit = parseRequestedLimit(limit);

        ServiceLimits serviceLimits = serviceConfigSource.limits();
        if (serviceLimits.defaultPageLimit() > serviceLimits.maxMapPageLimit()) {
            throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
        }
        int effectiveLimit = requestedLimit == NO_REQUESTED_LIMIT
                ? serviceLimits.defaultPageLimit() : requestedLimit;
        Instant now = clock.instant();
        Instant configuredExpiry = paginationProperties.requireCursorExpiry(now);
        if (effectiveLimit < 1 || effectiveLimit > serviceLimits.maxMapPageLimit()) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST, FieldError.outOfRange("limit"));
        }
        String context = pageContext(box, effectiveLimit);
        CursorState cursorState = cursor == null
                ? new CursorState(null, null, configuredExpiry)
                : decodeCursor(cursor, userId, now, context);

        int smoothing = matchingProperties.placeVibeSmoothing();
        long fetchLimit = (long) effectiveLimit + 1L;
        List<PlaceRepository.VisiblePlaceRow> rows = placeRepository.findVisiblePage(userId,
                box.westLng(), box.southLat(), box.eastLng(), box.northLat(),
                cursorState.afterId(), cursorState.upperId(), effectiveLimit, fetchLimit);

        List<PlaceResponse> items = rows.stream()
                .map(row -> PlaceResponse.from(row, smoothing))
                .toList();
        return new PlacePageResponse(items, pageInfo(userId, rows, context, cursorState.expiresAt()));
    }

    private PageInfo pageInfo(UUID userId, List<PlaceRepository.VisiblePlaceRow> rows,
                              String context, Instant expiresAt) {
        if (rows.isEmpty() || !rows.getFirst().getHasMore()) {
            return PageInfo.last();
        }
        PlaceRepository.VisiblePlaceRow first = rows.getFirst();
        PlaceRepository.VisiblePlaceRow last = rows.getLast();
        String nextCursor = signedValueCodec.sign(new SignedClaims(SignedValuePurpose.PAGE_CURSOR, userId,
                CURSOR_VERSION, expiresAt, Map.of(
                CLAIM_CONTEXT, ContentHash.sha256Hex(context),
                CLAIM_AFTER, last.getId().toString(),
                CLAIM_UPPER, first.getUpperId().toString()
        )));
        return PageInfo.next(nextCursor);
    }

    private CursorState decodeCursor(String cursor, UUID userId, Instant now, String context) {
        if (cursor.isBlank()) {
            throw ContractError.of(ErrorCode.INVALID_CURSOR);
        }
        SignedClaims claims = signedValueCodec.verify(cursor, SignedValuePurpose.PAGE_CURSOR, userId,
                CURSOR_VERSION, now);
        String contextHash = claims.claim(CLAIM_CONTEXT);
        if (contextHash == null || !SHA256_HEX.matcher(contextHash).matches()) {
            throw ContractError.of(ErrorCode.INVALID_CURSOR);
        }
        if (!ContentHash.matches(context, contextHash)) {
            throw ContractError.of(ErrorCode.CURSOR_CONTEXT_MISMATCH);
        }
        if (!claims.claims().keySet().equals(CURSOR_CLAIMS)) {
            throw ContractError.of(ErrorCode.INVALID_CURSOR);
        }
        UUID afterId = canonicalClaim(claims, CLAIM_AFTER);
        UUID upperId = canonicalClaim(claims, CLAIM_UPPER);
        if (comparePgUuid(afterId, upperId) >= 0) {
            throw ContractError.of(ErrorCode.INVALID_CURSOR);
        }
        return new CursorState(afterId, upperId, claims.expiresAt());
    }

    private static UUID canonicalClaim(SignedClaims claims, String name) {
        String value = claims.claim(name);
        if (value == null || !CANONICAL_UUID.matcher(value).matches()) {
            throw ContractError.of(ErrorCode.INVALID_CURSOR);
        }
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw ContractError.of(ErrorCode.INVALID_CURSOR);
            }
            return parsed;
        } catch (IllegalArgumentException e) {
            throw ContractError.of(ErrorCode.INVALID_CURSOR);
        }
    }

    private static int comparePgUuid(UUID left, UUID right) {
        int mostSignificant = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        return mostSignificant != 0 ? mostSignificant
                : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }

    private static BoundingBox parseBoundingBox(String bbox) {
        if (bbox == null) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST, FieldError.required("bbox"));
        }
        String[] components = bbox.split(",", -1);
        if (components.length != 4) {
            throw invalidBoundingBox();
        }
        double westLng = parseBboxComponent(components[0]);
        double southLat = parseBboxComponent(components[1]);
        double eastLng = parseBboxComponent(components[2]);
        double northLat = parseBboxComponent(components[3]);
        if (westLng < -180.0 || westLng > 180.0 || eastLng < -180.0 || eastLng > 180.0
                || southLat < -90.0 || southLat > 90.0 || northLat < -90.0 || northLat > 90.0
                || !(westLng < eastLng) || !(southLat < northLat)) {
            throw invalidBoundingBox();
        }
        return new BoundingBox(westLng, southLat, eastLng, northLat);
    }

    private static double parseBboxComponent(String component) {
        String value = component.strip();
        if (!BBOX_COMPONENT.matcher(value).matches()) {
            throw invalidBoundingBox();
        }
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                throw invalidBoundingBox();
            }
            return parsed == 0.0 ? 0.0 : parsed;
        } catch (NumberFormatException e) {
            throw invalidBoundingBox();
        }
    }

    private static int parseRequestedLimit(String limit) {
        if (limit == null) {
            return NO_REQUESTED_LIMIT;
        }
        if (!LIMIT.matcher(limit).matches()) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST, FieldError.invalid("limit"));
        }
        try {
            return Integer.parseInt(limit);
        } catch (NumberFormatException e) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST, FieldError.invalid("limit"));
        }
    }

    private static String pageContext(BoundingBox box, int effectiveLimit) {
        return "places-map-v1\n"
                + "path=/v1/places\n"
                + "sort=pg-uuid-asc\n"
                + "bbox=" + box.canonical() + "\n"
                + "filter=none\n"
                + "limit=" + effectiveLimit;
    }

    private static ContractError invalidBoundingBox() {
        return ContractError.of(ErrorCode.INVALID_BBOX, FieldError.invalid("bbox"));
    }

    private record BoundingBox(double westLng, double southLat, double eastLng, double northLat) {
        private String canonical() {
            return Double.toString(westLng) + "," + Double.toString(southLat) + ","
                    + Double.toString(eastLng) + "," + Double.toString(northLat);
        }
    }

    private record CursorState(UUID afterId, UUID upperId, Instant expiresAt) {
    }
}
