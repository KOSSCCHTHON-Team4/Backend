package team4.emotionmap.report;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.account.AccountAccessService;
import team4.emotionmap.account.User;
import team4.emotionmap.account.UserRepository;
import team4.emotionmap.contracts.config.ServiceConfigSource;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.request.RequestCoordinator;
import team4.emotionmap.contracts.validation.TextRules;
import team4.emotionmap.memory.MemoryAccessService;
import team4.emotionmap.report.dto.ReportCreateRequest;
import team4.emotionmap.report.dto.ReportResponse;

@Service
@RequiredArgsConstructor
public class ReportService {
    private static final byte[] FINGERPRINT_PREFIX =
            "emotionmap:report-submission:v1\0".getBytes(StandardCharsets.UTF_8);

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final AccountAccessService accountAccessService;
    private final MemoryAccessService memoryAccessService;
    private final ServiceConfigSource serviceConfigSource;
    private final RequestCoordinator requestCoordinator;
    private final Clock clock;

    /**
     * The request coordinator owns every transaction used by this entry point. In particular, no
     * claim is abandoned after completion starts because its outcome can no longer be inferred.
     */
    public CreateResult create(UUID reporterId, UUID key, ReportCreateRequest request) {
        requireCurrentReporter(reporterId);
        String details = TextRules.normalizeOptionalText(request.details(),
                serviceConfigSource.current().limits().reportDetailsMaxCodePoints(),
                "details", ErrorCode.VALIDATION_ERROR);
        RequestCoordinator.Scope scope =
                new RequestCoordinator.Scope(reporterId, RequestCoordinator.Route.REPORTS, key);
        RequestCoordinator.Admission admission = requestCoordinator.claim(scope,
                fingerprint(request.memoryId(), request.reason(), details));
        if (admission instanceof RequestCoordinator.Replay replay) {
            return receiptFor(reporterId, replay.resource(), RequestCoordinator.CompletionKind.REPLAY);
        }

        RequestCoordinator.Claim claim = ((RequestCoordinator.Claimed) admission).claim();
        RequestCoordinator.Completion completion = requestCoordinator.complete(claim,
                () -> persistNewReport(reporterId, request.memoryId(), request.reason(), details));
        return receiptFor(reporterId, completion.resource(), completion.kind());
    }

    private RequestCoordinator.ResourceRef persistNewReport(
            UUID reporterId, UUID memoryId, ReportReason reason, String details
    ) {
        User reporter = userRepository.findByIdForUpdate(reporterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN"));
        AccountAccessService.requireActive(reporter);
        accountAccessService.requireAccess(reporterId, RequestCoordinator.Route.REPORTS.path());
        memoryAccessService.requireReadableForUpdate(reporterId, memoryId);
        Report report = reportRepository.save(Report.builder()
                .reporterId(reporterId)
                .memoryId(memoryId)
                .reason(reason)
                .details(details)
                .createdAt(clock.instant())
                .build());
        return new RequestCoordinator.ResourceRef(RequestCoordinator.Route.REPORTS, report.getId());
    }

    private CreateResult receiptFor(
            UUID reporterId, RequestCoordinator.ResourceRef resource, RequestCoordinator.CompletionKind kind
    ) {
        if (resource.route() != RequestCoordinator.Route.REPORTS) {
            throw ContractError.of(ErrorCode.SERVICE_UNAVAILABLE);
        }
        requireCurrentReporter(reporterId);
        Report report = reportRepository.findById(resource.id())
                .filter(candidate -> candidate.getReporterId().equals(reporterId))
                .orElseThrow(() -> ContractError.of(ErrorCode.RESOURCE_NOT_FOUND));
        return new CreateResult(ReportResponse.from(report), kind);
    }

    private void requireCurrentReporter(UUID reporterId) {
        accountAccessService.requireAccess(reporterId, RequestCoordinator.Route.REPORTS.path());
    }

    private static RequestCoordinator.Fingerprint fingerprint(
            UUID memoryId, ReportReason reason, String details
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(FINGERPRINT_PREFIX);
            updateField(digest, memoryId.toString());
            updateField(digest, reason.name());
            updateField(digest, details);
            return new RequestCoordinator.Fingerprint(1, digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
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

    public record CreateResult(ReportResponse response, RequestCoordinator.CompletionKind kind) {
    }
}
