package in.projecteka.consentmanager.consent.model.response;

import in.projecteka.consentmanager.consent.model.ConsentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentArtefactReference {
    private String id;
    private ConsentStatus consentStatus;
}
