package in.projecteka.consentmanager.dataflow;

import in.projecteka.consentmanager.clients.ClientError;
import in.projecteka.consentmanager.clients.ConsentManagerClient;
import in.projecteka.consentmanager.dataflow.model.AccessPeriod;
import in.projecteka.consentmanager.dataflow.model.ConsentArtefactRepresentation;
import in.projecteka.consentmanager.dataflow.model.ConsentStatus;
import in.projecteka.consentmanager.dataflow.model.DateRange;
import in.projecteka.consentmanager.dataflow.model.HIUReference;
import in.projecteka.consentmanager.dataflow.model.HealthInformationNotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.test.StepVerifier;

import java.util.Objects;

import static in.projecteka.consentmanager.dataflow.TestBuilders.consentArtefactRepresentation;
import static in.projecteka.consentmanager.dataflow.TestBuilders.dataFlowRequest;
import static in.projecteka.consentmanager.dataflow.TestBuilders.healthInformationNotificationRequest;
import static in.projecteka.consentmanager.dataflow.Utils.toDate;
import static in.projecteka.consentmanager.dataflow.Utils.toDateWithMilliSeconds;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class DataFlowRequesterTest {
    @Mock
    private DataFlowRequestRepository dataFlowRequestRepository;

    @Mock
    private ConsentManagerClient consentManagerClient;

    @Mock
    private PostDataFlowRequestApproval postDataFlowRequestApproval;

    private DataFlowRequester dataFlowRequester;

    @BeforeEach
    public void setUp() {
        initMocks(this);
        dataFlowRequester = new DataFlowRequester(consentManagerClient, dataFlowRequestRepository,
                postDataFlowRequestApproval);
    }

    @Test
    void shouldAcceptDataFlowRequest() {
        String hiuId = "10000005";
        var request = dataFlowRequest()
                .dateRange(DateRange.builder()
                        .from(toDate("2020-01-15T08:47:48"))
                        .to(toDate("2020-01-20T08:47:48")).build())
                .build();
        var consentArtefactRepresentation = consentArtefactRepresentation().build();
        consentArtefactRepresentation.setStatus(ConsentStatus.GRANTED);
        consentArtefactRepresentation.getConsentDetail().setHiu(HIUReference.builder().id(hiuId).name("MAX").build());
        consentArtefactRepresentation.getConsentDetail().getPermission().setDataEraseAt(toDateWithMilliSeconds("253379772420000"));
        consentArtefactRepresentation.getConsentDetail().getPermission().
                setDateRange(AccessPeriod.builder()
                        .fromDate(toDate("2020-01-15T08:47:48"))
                        .toDate(toDate("2020-01-20T08:47:48"))
                        .build());

        when(consentManagerClient.getConsentArtefact(request.getConsent().getId()))
                .thenReturn(Mono.just(consentArtefactRepresentation));
        when(dataFlowRequestRepository.addDataFlowRequest(anyString(),
                any(in.projecteka.consentmanager.dataflow.model.DataFlowRequest.class)))
                .thenReturn(Mono.create(MonoSink::success));
        when(postDataFlowRequestApproval.broadcastDataFlowRequest(anyString(),
                any(in.projecteka.consentmanager.dataflow.model.DataFlowRequest.class))).thenReturn(Mono.empty());

        StepVerifier.create(dataFlowRequester.requestHealthData(request))
                .expectNextMatches(Objects::nonNull)
                .verifyComplete();
    }

    @Test
    public void shouldThrowInvalidHIU() {
        in.projecteka.consentmanager.dataflow.model.DataFlowRequest request = dataFlowRequest().build();
        ConsentArtefactRepresentation consentArtefactRepresentation = consentArtefactRepresentation().build();

        when(consentManagerClient.getConsentArtefact(request.getConsent().getId()))
                .thenReturn(Mono.just(consentArtefactRepresentation));
        when(postDataFlowRequestApproval.broadcastDataFlowRequest(anyString(),
                any(in.projecteka.consentmanager.dataflow.model.DataFlowRequest.class))).thenReturn(Mono.empty());

        StepVerifier.create(dataFlowRequester.requestHealthData(request))
                .expectErrorMatches(e -> (e instanceof ClientError) && ((ClientError) e).getHttpStatus().is4xxClientError());
    }

    @Test
    void shouldNotifyHealthInfoStatus() {
        var healthInformationNotificationRequest = healthInformationNotificationRequest().build();

        when(dataFlowRequestRepository.getIfPresent(healthInformationNotificationRequest.getRequestId()))
                .thenReturn(Mono.empty());
        when(dataFlowRequestRepository.saveHealthNotificationRequest(healthInformationNotificationRequest))
                .thenReturn(Mono.create(MonoSink::success));

        StepVerifier.create(dataFlowRequester.notifyHealthInformationStatus(healthInformationNotificationRequest))
                .verifyComplete();

        verify(dataFlowRequestRepository,
                times(1)).saveHealthNotificationRequest(healthInformationNotificationRequest);
        verify(dataFlowRequestRepository,
                times(1)).getIfPresent(healthInformationNotificationRequest.getRequestId());
    }
}
