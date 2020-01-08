package in.org.projecteka.hdaf.link;

import in.org.projecteka.hdaf.link.link.ClientError;
import in.org.projecteka.hdaf.link.link.model.PatientLinkReferenceRequest;
import in.org.projecteka.hdaf.link.link.model.PatientLinkReferenceResponse;
import in.org.projecteka.hdaf.link.link.model.PatientLinkRequest;
import in.org.projecteka.hdaf.link.link.model.PatientLinkResponse;
import in.org.projecteka.hdaf.link.link.model.ErrorRepresentation;
import in.org.projecteka.hdaf.link.link.model.hip.Patient;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static in.org.projecteka.hdaf.link.link.Transformer.toHIPPatient;
import static java.util.function.Predicate.not;

public class HIPClient {

    private final WebClient.Builder webClientBuilder;

    public HIPClient(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    public Mono<PatientLinkReferenceResponse> linkPatientCareContext(
            String patientId, PatientLinkReferenceRequest patientLinkReferenceRequest, String url) {
        Patient patientInHIP = toHIPPatient(patientId, patientLinkReferenceRequest);

        in.org.projecteka.hdaf.link.link.model.hip.PatientLinkReferenceRequest patientLinkReferenceRequestHIP =
                new in.org.projecteka.hdaf.link.link.model.hip.PatientLinkReferenceRequest(
                        patientLinkReferenceRequest.getTransactionId(), patientInHIP);

        return webClientBuilder.build()
                .post()
                .uri(String.format("%s/patients/link", url))
                .body(Mono.just(patientLinkReferenceRequestHIP), PatientLinkReferenceRequest.class)
                .retrieve()
                .onStatus(not(HttpStatus::is2xxSuccessful), clientResponse ->
                        clientResponse.bodyToMono(ErrorRepresentation.class)
                                .flatMap(e -> Mono.error(new ClientError(clientResponse.statusCode(), e))))
                .bodyToMono(PatientLinkReferenceResponse.class);
    }

    public Mono<PatientLinkResponse> validateToken(String linkRefNumber,
                                                   PatientLinkRequest patientLinkRequest, String url) {
        return webClientBuilder.build()
                .post()
                .uri(String.format("%s/patients/link/%s", url, linkRefNumber))
                .body(Mono.just(patientLinkRequest), PatientLinkRequest.class)
                .retrieve()
                .onStatus(not(HttpStatus::is2xxSuccessful), clientResponse ->
                        clientResponse.bodyToMono(ErrorRepresentation.class)
                                .flatMap(e -> Mono.error(new ClientError(clientResponse.statusCode(), e))))
                .bodyToMono(PatientLinkResponse.class);
    }
}
