package in.projecteka.consentmanager.dataflow;

import in.projecteka.consentmanager.DestinationsConfig;
import in.projecteka.consentmanager.MessageListenerContainerFactory;
import in.projecteka.consentmanager.clients.ClientError;
import in.projecteka.consentmanager.clients.ClientRegistryClient;
import in.projecteka.consentmanager.clients.DataRequestNotifier;
import in.projecteka.consentmanager.clients.model.Identifier;
import in.projecteka.consentmanager.consent.ConsentArtefactBroadcastListener;
import in.projecteka.consentmanager.dataflow.model.DataFlowRequestMessage;
import in.projecteka.consentmanager.dataflow.model.hip.DataFlowRequest;
import lombok.AllArgsConstructor;
import org.apache.log4j.Logger;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;

import static in.projecteka.consentmanager.ConsentManagerConfiguration.HIP_DATA_FLOW_REQUEST_QUEUE;
import static in.projecteka.consentmanager.clients.ClientError.queueNotFound;

@AllArgsConstructor
public class DataFlowBroadcastListener {
    private static final Logger logger = Logger.getLogger(ConsentArtefactBroadcastListener.class);
    private MessageListenerContainerFactory messageListenerContainerFactory;
    private DestinationsConfig destinationsConfig;
    private Jackson2JsonMessageConverter converter;
    private DataRequestNotifier dataRequestNotifier;
    private DataFlowRequestRepository dataFlowRequestRepository;
    private ClientRegistryClient clientRegistryClient;

    @PostConstruct
    public void subscribe() throws ClientError{
        DestinationsConfig.DestinationInfo destinationInfo = destinationsConfig
                .getQueues()
                .get(HIP_DATA_FLOW_REQUEST_QUEUE);
        if (destinationInfo == null) {
            logger.error(HIP_DATA_FLOW_REQUEST_QUEUE + " not found");
            throw queueNotFound();
        }

        MessageListenerContainer mlc = messageListenerContainerFactory
                .createMessageListenerContainer(destinationInfo.getRoutingKey());

        MessageListener messageListener = message -> {
            DataFlowRequestMessage dataFlowRequestMessage =
                    (DataFlowRequestMessage) converter.fromMessage(message);
            logger.info("Received message for Request id : " + dataFlowRequestMessage
                    .getTransactionId());
            DataFlowRequest dataFlowRequest = DataFlowRequest.builder()
                    .transactionId(dataFlowRequestMessage.getTransactionId())
                    .callBackUrl(dataFlowRequestMessage.getDataFlowRequest().getCallBackUrl())
                    .consent(dataFlowRequestMessage.getDataFlowRequest().getConsent())
                    .hiDataRange(dataFlowRequestMessage.getDataFlowRequest().getHiDataRange())
                    .build();

            dataFlowRequestRepository.getHipIdFor(dataFlowRequest.getConsent().getId())
                    .flatMap(hipId -> providerUrl(hipId))
                    .flatMap(url -> dataRequestNotifier.notifyHip(dataFlowRequest, url))
                    .block();

        };
        mlc.setupMessageListener(messageListener);

        mlc.start();
    }

    private Mono<String> providerUrl(String providerId) {
        return clientRegistryClient.providerWith(providerId)
                .flatMap(provider ->
                        provider.getIdentifiers()
                        .stream()
                        .filter(Identifier::isOfficial)
                        .findFirst()
                        .map(identifier -> Mono.just(identifier.getSystem()))
                        .orElse(Mono.empty()))
                .flatMap(url -> {
                    if (url == null){
                        logger.error("Hip Url not found for Hip Id");
                    }
                    return Mono.empty();
                });
    }
}