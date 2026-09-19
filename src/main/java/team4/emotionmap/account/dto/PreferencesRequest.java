package team4.emotionmap.account.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record PreferencesRequest(
        @NotNull @Valid Atmospheres atmospheres,
        @JsonProperty(required = true) String preferenceDescription,
        @NotNull @Pattern(regexp = "[1-9][0-9]*") String expectedPreferenceVersion
) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        if (name.equals("mailbox") || name.equals("mailboxLat") || name.equals("mailboxLng")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "IMMUTABLE_FIELD");
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }
}
