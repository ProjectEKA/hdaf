package in.projecteka.consentmanager.dataflow;

import com.nimbusds.jose.jwk.JWKSet;
import in.projecteka.consentmanager.DestinationsConfig;
import in.projecteka.consentmanager.clients.ConsentManagerClient;
import in.projecteka.consentmanager.clients.DataFlowRequestClient;
import in.projecteka.consentmanager.clients.DataRequestNotifier;
import in.projecteka.consentmanager.consent.ConceptValidator;
import in.projecteka.consentmanager.consent.ConsentRequestNotificationListener;
import in.projecteka.consentmanager.consent.HipConsentNotificationListener;
import in.projecteka.consentmanager.consent.HiuConsentNotificationListener;
import in.projecteka.consentmanager.dataflow.model.AccessPeriod;
import in.projecteka.consentmanager.dataflow.model.ConsentArtefactRepresentation;
import in.projecteka.consentmanager.dataflow.model.ConsentPermission;
import in.projecteka.consentmanager.dataflow.model.ConsentStatus;
import in.projecteka.consentmanager.dataflow.model.DataFlowRequest;
import in.projecteka.consentmanager.dataflow.model.DataFlowRequestResponse;
import in.projecteka.consentmanager.dataflow.model.DateRange;
import in.projecteka.consentmanager.dataflow.model.HIUReference;
import in.projecteka.consentmanager.dataflow.model.hip.DataRequest;
import in.projecteka.library.clients.model.Error;
import in.projecteka.library.clients.model.ErrorCode;
import in.projecteka.library.clients.model.ErrorRepresentation;
import in.projecteka.library.common.CentralRegistry;
import in.projecteka.library.common.GatewayTokenVerifier;
import in.projecteka.library.common.RequestValidator;
import in.projecteka.library.common.ServiceCaller;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static in.projecteka.consentmanager.common.TestBuilders.OBJECT_MAPPER;
import static in.projecteka.consentmanager.common.TestBuilders.gatewayDataFlowRequest;
import static in.projecteka.consentmanager.dataflow.TestBuilders.consentArtefactRepresentation;
import static in.projecteka.consentmanager.dataflow.TestBuilders.dataFlowRequest;
import static in.projecteka.consentmanager.dataflow.TestBuilders.dataRequest;
import static in.projecteka.consentmanager.dataflow.TestBuilders.string;
import static in.projecteka.consentmanager.dataflow.Utils.toDate;
import static in.projecteka.consentmanager.dataflow.Utils.toDateWithMilliSeconds;
import static in.projecteka.library.common.Role.GATEWAY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ContextConfiguration(initializers = DataFlowRequesterUserJourneyTest.ContextInitializer.class)
class DataFlowRequesterUserJourneyTest {
    private static final MockWebServer consentManagerServer = new MockWebServer();
    private static final MockWebServer identityServer = new MockWebServer();
    private static final MockWebServer gatewayServer = new MockWebServer();

    @SuppressWarnings("unused")
    @MockBean
    private DestinationsConfig destinationsConfig;

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private DataFlowRequestRepository dataFlowRequestRepository;

    @MockBean
    private PostDataFlowRequestApproval postDataFlowRequestApproval;

    @SuppressWarnings("unused")
    @MockBean
    private HiuConsentNotificationListener hiuConsentNotificationListener;

    @SuppressWarnings("unused")
    @MockBean
    private HipConsentNotificationListener hipConsentNotificationListener;

    @MockBean
    private DataFlowBroadcastListener dataFlowBroadcastListener;

    @MockBean
    private CentralRegistry centralRegistry;

    @SuppressWarnings("unused")
    @MockBean
    private DataRequestNotifier dataRequestNotifier;

    @SuppressWarnings("unused")
    @MockBean
    private ConsentRequestNotificationListener consentRequestNotificationListener;

    @SuppressWarnings("unused")
    @MockBean(name = "gatewayJWKSet")
    private JWKSet centralRegistryJWKSet;

    @SuppressWarnings("unused")
    @MockBean(name = "identityServiceJWKSet")
    private JWKSet identityServiceJWKSet;

    @MockBean
    private GatewayTokenVerifier gatewayTokenVerifier;

    @SuppressWarnings("unused")
    @MockBean
    private ConceptValidator conceptValidator;

    @MockBean
    private RequestValidator validator;

    @MockBean
    private DataFlowRequestClient dataFlowRequestClient;

    @MockBean
    private ConsentManagerClient consentManagerClient;


    @AfterAll
    static void tearDown() throws IOException {
        consentManagerServer.shutdown();
        identityServer.shutdown();
        gatewayServer.shutdown();
    }

    @Test
    void shouldAcknowledgeDataFlowRequest() throws IOException {
        String token = string();
        var hiuId = "10000005";
        var dataFlowRequest = dataFlowRequest().dateRange(DateRange.builder()
                .from(toDate("2020-01-16T08:47:48"))
                .to(toDate("2020-01-20T08:47:48"))
                .build()).build();
        ConsentArtefactRepresentation consentArtefactRepresentation =
                consentArtefactRepresentation().status(ConsentStatus.GRANTED).build();
        consentArtefactRepresentation.getConsentDetail().getPermission().
                setDateRange(AccessPeriod.builder()
                        .fromDate(toDate("2020-01-15T08:47:48"))
                        .toDate(toDate("2020-01-29T08:47:48"))
                        .build());
        consentArtefactRepresentation.getConsentDetail().setHiu(HIUReference.builder().id(hiuId).name("MAX").build());
        LocalDateTime consentExpiryDate = LocalDateTime.now().plusSeconds(9000000);
        consentArtefactRepresentation.getConsentDetail().getPermission().setDataEraseAt(consentExpiryDate);
        var consentArtefactRepresentationJson = OBJECT_MAPPER.writeValueAsString(consentArtefactRepresentation);
        consentManagerServer.enqueue(
                new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(consentArtefactRepresentationJson));
        identityServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{}"));
        when(validator.put(anyString(), any())).thenReturn(Mono.empty());
        when(gatewayTokenVerifier.verify(token)).thenReturn(Mono.just(new ServiceCaller(hiuId, List.of())));
        when(postDataFlowRequestApproval.broadcastDataFlowRequest(anyString(), any(DataFlowRequest.class)))
                .thenReturn(Mono.empty());
        when(dataFlowRequestRepository.addDataFlowRequest(anyString(), any(DataFlowRequest.class)))
                .thenReturn(Mono.create(MonoSink::success));

        webTestClient
                .post()
                .uri("/health-information/request")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dataFlowRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(DataFlowRequestResponse.class)
                .value(DataFlowRequestResponse::getTransactionId, Matchers.notNullValue());
    }

    @Test
    void shouldThrowConsentArtefactExpired() throws IOException {
        String token = string();
        var session = "{\"accessToken\": \"eyJhbGc\", \"refreshToken\": \"eyJhbGc\"}";
        var hiuId = "10000005";
        var dataFlowRequest = dataFlowRequest()
                .dateRange(DateRange.builder()
                        .from(toDate("2020-01-14T08:47:48"))
                        .to(toDate("2020-01-20T08:47:48")).build())
                .build();

        var consentArtefactRepresentation = consentArtefactRepresentation().build();
        consentArtefactRepresentation.getConsentDetail().setHiu(HIUReference.builder().id(hiuId).name("MAX").build());
        ConsentPermission permission = consentArtefactRepresentation
                .getConsentDetail()
                .getPermission();
        permission.setDateRange(AccessPeriod.builder().fromDate(toDate("2020-01-14T08:47:48")).toDate(toDate("2020" +
                "-01-20T08:47:48")).build());
        permission.setDataEraseAt(toDate("2020-01-15T08:47:48"));
        var consentArtefactRepresentationJson = OBJECT_MAPPER.writeValueAsString(consentArtefactRepresentation);
        var errorResponse = new ErrorRepresentation(new Error(ErrorCode.CONSENT_ARTEFACT_EXPIRED,
                "Consent artefact expired"));
        var errorResponseJson = OBJECT_MAPPER.writeValueAsString(errorResponse);
        consentManagerServer.enqueue(
                new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(consentArtefactRepresentationJson));
        identityServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(session));
        when(gatewayTokenVerifier.verify(token)).thenReturn(Mono.just(new ServiceCaller(hiuId, List.of())));
        when(postDataFlowRequestApproval.broadcastDataFlowRequest(anyString(), any(DataFlowRequest.class)))
                .thenReturn(Mono.empty());

        webTestClient
                .post()
                .uri("/health-information/request")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dataFlowRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isEqualTo(410)
                .expectBody()
                .json(errorResponseJson);
    }

    @Test
    void shouldThrowInvalidDateRange() throws IOException {
        String token = string();
        var hiuId = "10000005";
        var dataFlowRequest = dataFlowRequest()
                .dateRange(DateRange.builder()
                        .from(toDate("2020-01-14T08:47:48"))
                        .to(toDate("2020-01-20T08:47:48")).build())
                .build();
        var consentArtefactRepresentation = consentArtefactRepresentation().build();
        consentArtefactRepresentation.getConsentDetail().getPermission().
                setDateRange(AccessPeriod.builder()
                        .fromDate(toDate("2020-01-15T08:47:48"))
                        .toDate(toDate("2020-01-29T08:47:48"))
                        .build());
        consentArtefactRepresentation.setStatus(ConsentStatus.GRANTED);
        consentArtefactRepresentation.getConsentDetail().setHiu(HIUReference.builder().id(hiuId).name("MAX").build());
        LocalDateTime consentExpiryDate = LocalDateTime.now().plusMinutes(1);
        consentArtefactRepresentation.getConsentDetail().getPermission().setDataEraseAt(consentExpiryDate);
        var consentArtefactRepresentationJson = OBJECT_MAPPER.writeValueAsString(consentArtefactRepresentation);
        var errorResponse = new ErrorRepresentation(new Error(ErrorCode.INVALID_DATE_RANGE, "Date Range given is " +
                "invalid"));
        var errorResponseJson = OBJECT_MAPPER.writeValueAsString(errorResponse);
        consentManagerServer.enqueue(
                new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(consentArtefactRepresentationJson));
        var user = "{\"preferred_username\": \"service-account-10000005\"}";
        identityServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(user));

        when(gatewayTokenVerifier.verify(token)).thenReturn(Mono.just(new ServiceCaller(hiuId, List.of())));
        when(postDataFlowRequestApproval.broadcastDataFlowRequest(anyString(), any(DataFlowRequest.class)))
                .thenReturn(Mono.empty());

        webTestClient
                .post()
                .uri("/health-information/request")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dataFlowRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .json(errorResponseJson);
    }

    @Test
    void shouldThrowConsentNotGranted() throws IOException {
        String token = string();
        var hiuId = "10000005";
        var dataFlowRequest = dataFlowRequest()
                .dateRange(DateRange.builder()
                        .from(toDate("2020-01-14T08:47:48"))
                        .to(toDate("2020-01-20T08:47:48")).build())
                .build();
        var consentArtefactRepresentation = consentArtefactRepresentation().status(ConsentStatus.REVOKED).build();
        consentArtefactRepresentation.getConsentDetail().setHiu(HIUReference.builder().id(hiuId).name("MAX").build());
        LocalDateTime consentExpiryDate = LocalDateTime.now().plusSeconds(9000000);
        consentArtefactRepresentation.getConsentDetail().getPermission().setDataEraseAt(consentExpiryDate);
        var consentArtefactRepresentationJson = OBJECT_MAPPER.writeValueAsString(consentArtefactRepresentation);
        var errorResponse = new ErrorRepresentation(new Error(ErrorCode.CONSENT_NOT_GRANTED, "Not a granted consent."));
        var errorResponseJson = OBJECT_MAPPER.writeValueAsString(errorResponse);
        consentManagerServer.enqueue(
                new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(consentArtefactRepresentationJson));
        var user = "{\"preferred_username\": \"service-account-10000005\"}";
        identityServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(user));
        when(gatewayTokenVerifier.verify(token)).thenReturn(Mono.just(new ServiceCaller(hiuId, List.of())));
        when(postDataFlowRequestApproval.broadcastDataFlowRequest(anyString(), any(DataFlowRequest.class)))
                .thenReturn(Mono.empty());

        webTestClient
                .post()
                .uri("/health-information/request")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dataFlowRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isEqualTo(412)
                .expectBody()
                .json(errorResponseJson);
    }

    @Test
    void shouldSendDataRequestToHip() {
        DataRequest dataRequest = dataRequest().build();
        String hipId = string();
        String consentId = string();
        ConsentArtefactRepresentation caRep = ConsentArtefactRepresentation.builder().build();

        when(consentManagerClient.getConsentArtefact(consentId)).thenReturn(Mono.just(caRep));
        when(dataRequestNotifier.notifyHip(dataRequest, hipId)).thenReturn(Mono.empty());

        dataFlowBroadcastListener.configureAndSendDataRequestFor(dataRequest);

        verify(dataFlowBroadcastListener).configureAndSendDataRequestFor(dataRequest);
    }

    @Test
    void shouldSendDataRequestToGateway() throws IOException {
        String token = string();
        var hiuId = "10000005";
        var dataFlowRequest = gatewayDataFlowRequest().build();
        dataFlowRequest.getHiRequest().getDateRange().setFrom(toDate("2020-01-15T08:47:48"));
        dataFlowRequest.getHiRequest().getDateRange().setTo(toDate("2020-01-20T08:47:48"));
        var consentArtefact = consentArtefactRepresentation().build();
        consentArtefact.setStatus(ConsentStatus.GRANTED);
        consentArtefact.getConsentDetail().setHiu(HIUReference.builder().id(hiuId).name("MAX").build());
        consentArtefact.getConsentDetail().getPermission().setDataEraseAt(toDateWithMilliSeconds("253379772420000"));
        consentArtefact.getConsentDetail().getPermission().
                setDateRange(AccessPeriod.builder()
                        .fromDate(toDate("2020-01-15T08:47:48"))
                        .toDate(toDate("2020-01-20T08:47:48"))
                        .build());
        var consentArtefactRepresentationJson = OBJECT_MAPPER.writeValueAsString(consentArtefact);
        consentManagerServer.enqueue(
                new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(consentArtefactRepresentationJson));
        identityServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{}"));
        gatewayServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{}"));
        when(validator.put(anyString(), any())).thenReturn(Mono.empty());
        when(validator.validate(anyString(), any())).thenReturn(Mono.just(Boolean.TRUE));
        when(gatewayTokenVerifier.verify(token)).thenReturn(Mono.just(new ServiceCaller(hiuId, List.of(GATEWAY))));
        when(consentManagerClient.getConsentArtefact(dataFlowRequest.getHiRequest().getConsent().getId()))
                .thenReturn(Mono.just(consentArtefact));
        when(dataFlowRequestRepository.addDataFlowRequest(anyString(),
                any(in.projecteka.consentmanager.dataflow.model.DataFlowRequest.class)))
                .thenReturn(Mono.empty());
        when(dataFlowRequestClient.sendHealthInformationResponseToGateway(any(), eq(hiuId)))
                .thenReturn(Mono.empty());

        webTestClient
                .post()
                .uri(Constants.PATH_HEALTH_INFORMATION_REQUEST)
                .header(AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dataFlowRequest)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isAccepted();
    }

    static class ContextInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            TestPropertyValues values =
                    TestPropertyValues.of(
                            Stream.of("consentmanager.dataflow.authserver.url=" + consentManagerServer.url(""),
                                    "consentmanager.dataflow.authserver.clientId=1",
                                    "consentmanager.dataflow.authserver.clientSecret=NCG_CM",
                                    "consentmanager.dataflow.consentmanager.url=" + consentManagerServer.url(""),
                                    "consentmanager.keycloak.baseUrl=" + identityServer.url(""),
                                    "consenmanager.dataflow.gatewayserver.url=" + gatewayServer.url("")));
            values.applyTo(applicationContext);
        }
    }
}
