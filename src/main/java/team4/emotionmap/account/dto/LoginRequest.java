package team4.emotionmap.account.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.account.EmailPasswordCredential;

/** Email-password authentication; passwords are never normalized. */
public record LoginRequest(
        @NotBlank @Email @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) String email,
        @NotEmpty @JsonProperty(required = true, access = JsonProperty.Access.WRITE_ONLY)
        @JsonSetter(nulls = Nulls.FAIL) String password
) {
    public LoginRequest {
        if (email != null) {
            email = EmailPasswordCredential.normalizeEmailLookupKey(email);
        }
    }

    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }

    @Override
    public String toString() {
        return "LoginRequest[REDACTED]";
    }
}
