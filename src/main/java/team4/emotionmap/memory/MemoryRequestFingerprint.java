package team4.emotionmap.memory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.geo.GeoPoint;
import team4.emotionmap.contracts.request.RequestCoordinator;
import team4.emotionmap.contracts.validation.StrictValues;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.memory.dto.MemoryCreateRequest;

/** Stable, versioned identity of a memory-create intent before any consumable input is touched. */
final class MemoryRequestFingerprint {
    private static final byte[] PREFIX = "emotionmap:memory-create:v1\0".getBytes(StandardCharsets.UTF_8);

    private MemoryRequestFingerprint() {
    }

    static RequestCoordinator.Fingerprint fingerprint(MemoryCreateRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(PREFIX);
            updateField(digest, request.type() == null ? null : request.type().name());
            updateCoordinate(digest, request.lat());
            updateCoordinate(digest, request.lng());
            updateField(digest, request.content());
            updateUuid(digest, request.imageId());
            updateAtmospheres(digest, request.atmospheres());
            updateFields(digest, request.categoryCodes());
            updateField(digest, request.analysisToken());
            updateUuid(digest, request.placeId());
            updateField(digest, normalizeLabel(request.placeLabel()));
            String naverTitle = normalizeNaverTitle(request.naverTitle());
            updateField(digest, naverTitle);
            updateField(digest, naverTitle == null ? null : normalizeNaverField(request.naverAddress()));
            updateField(digest, normalizeNaverField(request.naverCategory()));
            return new RequestCoordinator.Fingerprint(1, digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    static GeoPoint normalizedCoordinates(MemoryCreateRequest request) {
        GeoPoint coordinates = StrictValues.requireCoordinates(
                request.lat(), request.lng(), "lat", "lng", ErrorCode.VALIDATION_ERROR);
        return new GeoPoint(normalizeZero(coordinates.lat()), normalizeZero(coordinates.lng()));
    }

    static String normalizeLabel(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    static String normalizeNaverTitle(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    static String normalizeNaverField(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static void updateCoordinate(MessageDigest digest, Double value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }
        digest.update((byte) 1);
        updateLong(digest, Double.doubleToRawLongBits(normalizeZero(value)));
    }

    private static void updateAtmospheres(MessageDigest digest, Atmospheres atmospheres) {
        if (atmospheres == null) {
            digest.update((byte) 0);
            return;
        }
        digest.update((byte) 1);
        updateLong(digest, Double.doubleToRawLongBits(atmospheres.crowdLevel()));
        updateLong(digest, Double.doubleToRawLongBits(atmospheres.spatialFeel()));
        updateLong(digest, Double.doubleToRawLongBits(atmospheres.companyFit()));
        updateLong(digest, Double.doubleToRawLongBits(atmospheres.stayStyle()));
    }

    private static void updateUuid(MessageDigest digest, UUID value) {
        updateField(digest, value == null ? null : value.toString());
    }

    private static void updateFields(MessageDigest digest, List<String> values) {
        if (values == null) {
            digest.update((byte) 0);
            return;
        }
        digest.update((byte) 1);
        updateLength(digest, values.size());
        for (String value : values) {
            updateField(digest, value);
        }
    }

    private static void updateField(MessageDigest digest, String value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 1);
        updateLength(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateLength(MessageDigest digest, int length) {
        digest.update((byte) (length >>> 24));
        digest.update((byte) (length >>> 16));
        digest.update((byte) (length >>> 8));
        digest.update((byte) length);
    }

    private static void updateLong(MessageDigest digest, long value) {
        for (int shift = Long.SIZE - Byte.SIZE; shift >= 0; shift -= Byte.SIZE) {
            digest.update((byte) (value >>> shift));
        }
    }

    private static double normalizeZero(double value) {
        return value == 0.0 ? 0.0 : value;
    }
}
