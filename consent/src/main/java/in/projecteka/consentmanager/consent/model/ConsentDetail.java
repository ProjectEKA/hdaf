package in.projecteka.consentmanager.consent.model;

import in.projecteka.library.common.PatientReference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

import static in.projecteka.consentmanager.Constants.API_VERSION;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ConsentDetail {
    @Builder.Default
    private String schemaVersion = API_VERSION;
    private String consentId;
    private LocalDateTime createdAt;
    private PatientReference patient;
    private List<GrantedContext> careContexts;
    private ConsentPurpose purpose;
    private HIPReference hip;
    private HIUReference hiu;
    private CMReference consentManager;
    private Requester requester;
    private HIType[] hiTypes;
    private ConsentPermission permission;
}