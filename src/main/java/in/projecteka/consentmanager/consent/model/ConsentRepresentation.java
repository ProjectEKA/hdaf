package in.projecteka.consentmanager.consent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ConsentRepresentation {
    private ConsentStatus status;
    private ConsentArtefact consentDetail;
    private String consentRequestId;
}
