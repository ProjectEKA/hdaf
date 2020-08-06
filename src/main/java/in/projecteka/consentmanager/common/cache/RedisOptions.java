package in.projecteka.consentmanager.common.cache;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConditionalOnProperty(value = "consentmanager.cacheMethod", havingValue = "redis")
@ConfigurationProperties(prefix = "consentmanager.redis")
@Getter
@AllArgsConstructor
@ConstructorBinding
public class RedisOptions {
    private final String host;
    private final int port;
    private final String password;
    private final boolean keepAliveEnabled;
    private final ReadFrom readFrom;

    public io.lettuce.core.ReadFrom getReadFrom() {
        switch (readFrom) {
            case NEAREST:
                return io.lettuce.core.ReadFrom.NEAREST;
            case REPLICA:
                return io.lettuce.core.ReadFrom.REPLICA_PREFERRED;
            case UPSTREAM:
                return io.lettuce.core.ReadFrom.MASTER_PREFERRED;
            default:
                return io.lettuce.core.ReadFrom.ANY;
        }
    }
}
