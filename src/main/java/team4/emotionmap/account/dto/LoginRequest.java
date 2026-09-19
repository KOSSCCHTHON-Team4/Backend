package team4.emotionmap.account.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Email-password authentication; passwords are never normalized. */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotEmpty @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password
) {
    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }

    @Override
    public String toString() {
        return "LoginRequest[REDACTED]";
    }
}
