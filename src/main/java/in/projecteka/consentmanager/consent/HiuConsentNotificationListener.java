package in.projecteka.consentmanager.consent;

import in.projecteka.consentmanager.DestinationsConfig;
import in.projecteka.consentmanager.MessageListenerContainerFactory;
import in.projecteka.consentmanager.clients.ClientError;
import in.projecteka.consentmanager.clients.ConsentArtefactNotifier;
import in.projecteka.consentmanager.consent.model.ConsentArtefactsMessage;
import in.projecteka.consentmanager.consent.model.request.HIUNotificationRequest;
import in.projecteka.consentmanager.consent.model.response.ConsentArtefactReference;
import lombok.AllArgsConstructor;
import org.apache.log4j.Logger;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;

import static in.projecteka.consentmanager.ConsentManagerConfiguration.HIU_CONSENT_NOTIFICATION_QUEUE;
import static in.projecteka.consentmanager.clients.ClientError.queueNotFound;

@AllArgsConstructor
public class HiuConsentNotificationListener {
    private static final Logger logger = Logger.getLogger(HiuConsentNotificationListener.class);
    private MessageListenerContainerFactory messageListenerContainerFactory;
    private DestinationsConfig destinationsConfig;
    private Jackson2JsonMessageConverter converter;
    private ConsentArtefactNotifier consentArtefactNotifier;

    @PostConstruct
    public void subscribe() throws ClientError {
        DestinationsConfig.DestinationInfo destinationInfo = destinationsConfig
                .getQueues()
                .get(HIU_CONSENT_NOTIFICATION_QUEUE);
        if (destinationInfo == null) {
            logger.error(HIU_CONSENT_NOTIFICATION_QUEUE + " not found");
            throw queueNotFound();
        }

        MessageListenerContainer mlc = messageListenerContainerFactory
                .createMessageListenerContainer(destinationInfo.getRoutingKey());

        MessageListener messageListener = message -> {
            ConsentArtefactsMessage consentArtefactsMessage = (ConsentArtefactsMessage) converter.fromMessage(message);
            logger.info(String.format(
                    "Received message for Request id : %s", consentArtefactsMessage.getRequestId()));

            notifyHiu(consentArtefactsMessage);
        };
        mlc.setupMessageListener(messageListener);

        mlc.start();
    }

    private void notifyHiu(ConsentArtefactsMessage consentArtefactsMessage) {
        HIUNotificationRequest hiuNotificationRequest = hiuNotificationRequest(consentArtefactsMessage);
        String hiuCallBackUrl = consentArtefactsMessage.getHiuCallBackUrl();
        consentArtefactNotifier.notifyHiu(hiuNotificationRequest, hiuCallBackUrl).block();
    }

    private HIUNotificationRequest hiuNotificationRequest(ConsentArtefactsMessage consentArtefactsMessage) {
        List<ConsentArtefactReference> consentArtefactReferences = consentArtefactReferences(consentArtefactsMessage);

        return HIUNotificationRequest
                .builder()
                .consents(consentArtefactReferences)
                .consentRequestId(consentArtefactsMessage.getRequestId())
                .build();
    }

    private List<ConsentArtefactReference> consentArtefactReferences(ConsentArtefactsMessage consentArtefactsMessage) {
        return consentArtefactsMessage
                .getConsentArtefacts()
                .stream()
                .map(consentArtefact -> ConsentArtefactReference
                        .builder()
                        .status(consentArtefact.getStatus())
                        .id(consentArtefact.getConsentDetail().getConsentId())
                        .build())
                .collect(Collectors.toList());
    }
}
