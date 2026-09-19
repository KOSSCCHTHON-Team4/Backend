package team4.emotionmap.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockPart;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;

class ImageControllerStrictRequestTest {
    private final ImageUploadService service = mock(ImageUploadService.class);
    private final ImageController controller = new ImageController(service);
    private final UUID ownerId = UUID.randomUUID();

    @Test
    void rejectsMissingMalformedAndRepeatedIdempotencyHeadersBeforeService() {
        assertCode(requestWithParts(), ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        assertCode(requestWithParts("not-a-uuid"), ErrorCode.INVALID_REQUEST);
        MockHttpServletRequest repeated = requestWithParts(UUID.randomUUID().toString());
        repeated.addHeader("Idempotency-Key", UUID.randomUUID().toString());
        assertCode(repeated, ErrorCode.INVALID_REQUEST);
        MockHttpServletRequest comma = requestWithParts(UUID.randomUUID() + "," + UUID.randomUUID());
        assertCode(comma, ErrorCode.INVALID_REQUEST);
        verify(service, never()).upload(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsQueryAndAnyPartShapeOtherThanOneFilePart() {
        MockHttpServletRequest query = requestWithParts(UUID.randomUUID().toString());
        query.setQueryString("unsupported=true");
        assertCode(query, ErrorCode.INVALID_REQUEST);

        MockHttpServletRequest text = requestWithParts(UUID.randomUUID().toString());
        text.addPart(new MockPart("note", null, new byte[]{1}));
        assertCode(text, ErrorCode.INVALID_REQUEST);

        MockHttpServletRequest duplicateFile = requestWithParts(UUID.randomUUID().toString());
        duplicateFile.addPart(new MockPart("file", "second.jpg", new byte[]{2}));
        assertCode(duplicateFile, ErrorCode.INVALID_REQUEST);
        verify(service, never()).upload(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private MockHttpServletRequest requestWithParts(String... keys) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/images");
        if (keys.length > 0) {
            request.addHeader("Idempotency-Key", keys[0]);
        }
        request.addPart(new MockPart("file", "fixture.jpg", new byte[]{1}));
        return request;
    }

    private void assertCode(MockHttpServletRequest request, ErrorCode expected) {
        ContractError error = catchThrowableOfType(ContractError.class, () -> controller.upload(ownerId, request));
        assertThat(error.code()).isEqualTo(expected);
    }
}
