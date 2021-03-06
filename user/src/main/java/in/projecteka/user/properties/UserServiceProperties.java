package in.projecteka.user.properties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConfigurationProperties(prefix = "user")
@Getter
@AllArgsConstructor
@ConstructorBinding
@Builder
public class UserServiceProperties {
    private final int transactionPinDigitSize;
    private final int transactionPinTokenValidity;
    private final int userCreationTokenValidity;
    private final String userIdSuffix;
    private final int maxOtpAttempts;
    private final int maxOtpAttemptsPeriodInMin;
    private final int otpAttemptsBlockPeriodInMin;
    private final long maxIncorrectPinAttempts;
    private final int otpInvalidAttemptsBlockPeriodInMin;
    private final int otpMaxInvalidAttempts;
}
