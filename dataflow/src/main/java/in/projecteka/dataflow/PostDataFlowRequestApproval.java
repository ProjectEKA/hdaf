package in.projecteka.dataflow;

import in.projecteka.dataflow.model.DataFlowRequestMessage;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import reactor.core.publisher.Mono;

import static in.projecteka.dataflow.Constants.HIP_DATA_FLOW_REQUEST_QUEUE;

@AllArgsConstructor
@Slf4j
public class PostDataFlowRequestApproval {
    private final AmqpTemplate amqpTemplate;
    private final DestinationsConfig destinationsConfig;

    @SneakyThrows
    public Mono<Void> broadcastDataFlowRequest(
            String transactionId,
            in.projecteka.dataflow.model.DataFlowRequest dataFlowRequest) {
        DestinationsConfig.DestinationInfo destinationInfo =
                destinationsConfig.getQueues().get(HIP_DATA_FLOW_REQUEST_QUEUE);

        return Mono.create(monoSink -> {
            amqpTemplate.convertAndSend(
                    destinationInfo.getExchange(),
                    destinationInfo.getRoutingKey(),
                    DataFlowRequestMessage.builder()
                            .transactionId(transactionId)
                            .dataFlowRequest(dataFlowRequest)
                            .build());
            log.info("Broadcasting data flow request with transaction id : " + transactionId);
            monoSink.success();
        });
    }
}