package in.projecteka.consentmanager.consent;

import in.projecteka.consentmanager.common.TokenUtils;
import in.projecteka.consentmanager.consent.model.response.ConsentArtefactLightRepresentation;
import in.projecteka.consentmanager.consent.model.response.ConsentArtefactRepresentation;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@AllArgsConstructor
public class ConsentArtefactsController {

    private ConsentManager consentManager;

    @GetMapping(value = "/consents/{consentId}")
    public Mono<ConsentArtefactRepresentation> getConsentArtefact(
            @RequestHeader(value = "Authorization") String authorization,
            @PathVariable(value = "consentId") String consentId) {
        String requesterId = TokenUtils.getCallerId(authorization);
        return consentManager.getConsent(consentId, requesterId);
    }

    @GetMapping(value = "/internal/consents/{consentId}")
    public Mono<ConsentArtefactLightRepresentation> getConsent(@PathVariable String consentId) {
        return consentManager.getConsentArtefactLight(consentId);
    }

    @GetMapping(value = "/consent-requests/{request-id}/consent-artefacts")
    public Flux<ConsentArtefactRepresentation> getConsents(
            @PathVariable(value = "request-id") String requestId,
            @RequestHeader(value = "Authorization") String authorization) {
        String requesterId = TokenUtils.getCallerId(authorization);
        return consentManager.getConsents(requestId, requesterId);
    }
}
