package in.projecteka.consentmanager.user.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class OtpVerificationRequest {
    private final String username;
}
