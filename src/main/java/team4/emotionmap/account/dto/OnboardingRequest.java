package team4.emotionmap.account.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record OnboardingRequest(
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double mailboxLat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double mailboxLng,
        @NotNull @Valid Atmospheres atmospheres,
        @JsonProperty(required = true) String preferenceDescription
) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }
}
