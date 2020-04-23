package in.projecteka.consentmanager;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConstructorBinding
@ConfigurationProperties("consentmanager.db")
@Getter
@AllArgsConstructor
public class DbOptions {
    private String host;
    private int port;
    private String schema;
    private String user;
    private String password;
    private int poolSize;
}
