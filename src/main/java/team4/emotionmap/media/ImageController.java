package team4.emotionmap.media;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.request.RequestCoordinator;
import team4.emotionmap.contracts.validation.StrictValues;
import team4.emotionmap.media.dto.ImageUploadResponse;

@RestController
@RequestMapping("/v1/images")
public class ImageController {
    private final ImageUploadService imageUploadService;

    public ImageController(ImageUploadService imageUploadService) {
        this.imageUploadService = imageUploadService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageUploadResponse> upload(@AuthenticationPrincipal UUID userId,
                                                       HttpServletRequest request) {
        rejectQueryFields(request);
        UUID key = requireSingleIdempotencyKey(request);
        MultipartFile file = requireOnlyFilePart(request);
        ImageUploadService.UploadResult result = imageUploadService.upload(userId, key, file);
        HttpStatus status = result.kind() == RequestCoordinator.CompletionKind.CREATED
                ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }

    private static void rejectQueryFields(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query != null && !query.isEmpty()) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST);
        }
    }

    private static UUID requireSingleIdempotencyKey(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders("Idempotency-Key");
        if (values == null || !values.hasMoreElements()) {
            throw ContractError.of(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        String value = values.nextElement();
        if (values.hasMoreElements() || value == null || value.indexOf(',') >= 0) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST);
        }
        return StrictValues.requireUuid(value, "idempotencyKey", ErrorCode.INVALID_REQUEST);
    }

    private static MultipartFile requireOnlyFilePart(HttpServletRequest request) {
        Collection<Part> parts;
        try {
            parts = request.getParts();
        } catch (IOException | ServletException ignored) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST);
        }
        if (parts == null || parts.size() != 1) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST);
        }
        Part part = parts.iterator().next();
        if (!"file".equals(part.getName()) || part.getSubmittedFileName() == null) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST);
        }
        return new ServletPartMultipartFile(part);
    }

    private record ServletPartMultipartFile(Part part) implements MultipartFile {
        @Override
        public String getName() {
            return part.getName();
        }

        @Override
        public String getOriginalFilename() {
            return part.getSubmittedFileName();
        }

        @Override
        public String getContentType() {
            return part.getContentType();
        }

        @Override
        public boolean isEmpty() {
            return part.getSize() == 0;
        }

        @Override
        public long getSize() {
            return part.getSize();
        }

        @Override
        public byte[] getBytes() throws IOException {
            try (InputStream input = getInputStream()) {
                return input.readAllBytes();
            }
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return part.getInputStream();
        }

        @Override
        public void transferTo(File destination) throws IOException, IllegalStateException {
            part.write(destination.getPath());
        }
    }
}
