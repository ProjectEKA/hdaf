package in.org.projecteka.hdaf.link.discovery;

import in.org.projecteka.hdaf.clients.ClientRegistryClient;
import in.org.projecteka.hdaf.clients.HipServiceClient;
import in.org.projecteka.hdaf.clients.UserServiceClient;
import in.org.projecteka.hdaf.link.discovery.model.Address;
import in.org.projecteka.hdaf.link.discovery.model.Provider;
import in.org.projecteka.hdaf.link.discovery.model.Telecom;
import in.org.projecteka.hdaf.link.discovery.model.User;
import in.org.projecteka.hdaf.link.discovery.model.patient.request.Identifier;
import in.org.projecteka.hdaf.link.discovery.model.patient.request.Patient;
import in.org.projecteka.hdaf.link.discovery.model.patient.request.PatientRequest;
import in.org.projecteka.hdaf.link.discovery.model.patient.response.DiscoveryResponse;
import in.org.projecteka.hdaf.link.discovery.model.patient.response.HipPatientResponse;
import in.org.projecteka.hdaf.link.discovery.repository.DiscoveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static in.org.projecteka.hdaf.link.TestBuilders.address;
import static in.org.projecteka.hdaf.link.TestBuilders.discoveryResponse;
import static in.org.projecteka.hdaf.link.TestBuilders.hipPatientResponse;
import static in.org.projecteka.hdaf.link.TestBuilders.identifier;
import static in.org.projecteka.hdaf.link.TestBuilders.patientIdentifier;
import static in.org.projecteka.hdaf.link.TestBuilders.patientRequest;
import static in.org.projecteka.hdaf.link.TestBuilders.provider;
import static in.org.projecteka.hdaf.link.TestBuilders.providerIdentifier;
import static in.org.projecteka.hdaf.link.TestBuilders.telecom;
import static in.org.projecteka.hdaf.link.TestBuilders.user;
import static java.util.List.of;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;


public class DiscoveryTest {

    @Mock
    ClientRegistryClient clientRegistryClient;

    @Mock
    UserServiceClient userServiceClient;

    @Mock
    HipServiceClient hipServiceClient;

    @Mock
    DiscoveryRepository discoveryRepository;

    @BeforeEach
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void returnProvidersWithOfficial() {
        var discovery = new Discovery(clientRegistryClient, userServiceClient, hipServiceClient, discoveryRepository);
        var address = address().use("work").build();
        var telecommunication = telecom().use("work").build();
        var identifier = identifier().use(in.org.projecteka.hdaf.link.discovery.model.Identifier.IdentifierType.OFFICIAL.toString()).build();
        var provider = provider()
                .addresses(of(address))
                .telecoms(of(telecommunication))
                .identifiers(of(identifier))
                .name("Max")
                .build();
        when(clientRegistryClient.providersOf(eq("Max"))).thenReturn(Flux.just(provider));

        StepVerifier.create(discovery.providersFrom("Max"))
                .expectNext(Transformer.to(provider))
                .verifyComplete();
    }

    @Test
    public void patientForGivenProviderIdAndPatientId() {
        var discovery = new Discovery(clientRegistryClient, userServiceClient, hipServiceClient, discoveryRepository);
        Address address = address().use("work").build();
        Telecom telecom = telecom().use("work").build();
        HipPatientResponse hipPatientResponse = hipPatientResponse().patient(new in.org.projecteka.hdaf.link.discovery.model.patient.response.Patient("123", "John Doe", List.of(), List.of())).build();
        User user = user().identifier("1").firstName("first name").phoneNumber("9999999999").build();
        String hipClientUrl = "http://localhost:8001";
        Provider provider = provider()
                .addresses(List.of(address))
                .telecoms(List.of(telecom))
                .identifiers(List.of(providerIdentifier().system(hipClientUrl).use("official").build()))
                .name("Max")
                .build();
        Identifier identifier = patientIdentifier().type("MOBILE").value("9999999999").build();
        Patient patient = Patient.builder()
                .id(user.getIdentifier())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .gender(user.getGender())
                .dateOfBirth(user.getDateOfBirth())
                .verifiedIdentifiers(List.of(identifier))
                .unVerifiedIdentifiers(List.of())
                .build();
        String transactionId = "transaction-id";
        PatientRequest patientRequest = patientRequest().patient(patient).transactionId(transactionId).build();
        DiscoveryResponse discoveryResponse = discoveryResponse().patient(hipPatientResponse.getPatient()).transactionId(transactionId).build();

        when(clientRegistryClient.providerWith(eq("1"))).thenReturn(Mono.just(provider));
        when(userServiceClient.userOf(eq("1"))).thenReturn(Mono.just(user));
        when(hipServiceClient.patientFor(eq(patientRequest), eq(hipClientUrl))).thenReturn(Mono.just(hipPatientResponse));

        StepVerifier.create(discovery.patientFor("1", "1", transactionId))
                .expectNext(discoveryResponse)
                .verifyComplete();
    }

    @Test
    public void shouldGetInvalidHipErrorWhenIdentifierIsNotOfficial() {
        var discovery = new Discovery(clientRegistryClient, userServiceClient, hipServiceClient, discoveryRepository);
        Address address = address().use("work").build();
        Telecom telecom = telecom().use("work").build();
        User user = user().identifier("1").firstName("first name").phoneNumber("9999999999").build();
        String hipClientUrl = "http://localhost:8001";
        Provider provider = provider()
                .addresses(List.of(address))
                .telecoms(List.of(telecom))
                .identifiers(List.of(providerIdentifier().system(hipClientUrl).use("random").build()))
                .name("Max")
                .build();

        when(clientRegistryClient.providerWith(eq("1"))).thenReturn(Mono.just(provider));
        when(userServiceClient.userOf(eq("1"))).thenReturn(Mono.just(user));

        StepVerifier.create(discovery.patientFor("1", "1", UUID.randomUUID().toString()))
                .expectErrorMatches(error -> error.equals(new Throwable("Invalid HIP")));
    }

    @Test
    public void returnEmptyProvidersWhenOfficialIdentifierIsUnavailable() {
        var discovery = new Discovery(clientRegistryClient, userServiceClient, hipServiceClient, discoveryRepository);
        var address = address().use("work").build();
        var telecommunication = telecom().use("work").build();
        var identifier = identifier().build();
        var provider = provider()
                .addresses(of(address))
                .telecoms(of(telecommunication))
                .identifiers(of(identifier))
                .name("Max")
                .build();
        when(clientRegistryClient.providersOf(eq("Max"))).thenReturn(Flux.just(provider));

        StepVerifier.create(discovery.providersFrom("Max"))
                .verifyComplete();
    }
}