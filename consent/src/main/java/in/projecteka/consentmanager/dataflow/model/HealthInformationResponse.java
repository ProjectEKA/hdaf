package in.projecteka.consentmanager.dataflow.model;

import in.projecteka.consentmanager.link.discovery.model.patient.response.GatewayResponse;
import in.projecteka.library.clients.model.RespError;
import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class HealthInformationResponse {
    UUID requestId;
    LocalDateTime timestamp;
    AcknowledgementResponse hiRequest;
    @NotNull
    GatewayResponse resp;
    RespError error;
}
