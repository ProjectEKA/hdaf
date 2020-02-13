package in.projecteka.consentmanager;

import in.projecteka.consentmanager.user.AuthenticatorService;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity httpSecurity,
            ReactiveAuthenticationManager authenticationManager,
            ServerSecurityContextRepository securityContextRepository) {
        return httpSecurity
                .authorizeExchange()
                .pathMatchers("/**.json", "/users/verify","/users/permit").permitAll()
                .pathMatchers("/**.html").permitAll()
                .pathMatchers("/**.js").permitAll()
                .pathMatchers("/**.png").permitAll()
                .pathMatchers("/**.css").permitAll()
                .pathMatchers("/**.yaml").permitAll()
                .pathMatchers("/**").authenticated()
                .and()
                .httpBasic().disable()
                .formLogin().disable()
                .csrf().disable()
                .logout().disable()
                .authenticationManager(authenticationManager)
                .securityContextRepository(securityContextRepository)
                .build();
    }

    @Bean
    public ReactiveAuthenticationManager authenticationManager() {
        return new AuthenticationManager();
    }

    @Bean
    public SecurityContextRepository contextRepository(ReactiveAuthenticationManager manager,
                                                       AuthenticatorService authenticatorService) {
        return new SecurityContextRepository(manager, authenticatorService);
    }

    @AllArgsConstructor
    private static class SecurityContextRepository implements ServerSecurityContextRepository {

        private ReactiveAuthenticationManager manager;
        private AuthenticatorService authenticatorService;

        @Override
        public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
            throw new UnsupportedOperationException("No need right now!");
        }

        @Override
        public Mono<SecurityContext> load(ServerWebExchange exchange) {
            var authToken = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            var notBlank = authToken != null && !authToken.trim().equals("");
            var isSignUpRequest = exchange.getRequest().getPath().toString().equals("/users")
                    && exchange.getRequest().getMethod().equals(HttpMethod.POST);

            if(isSignUpRequest && notBlank && authenticatorService.validateToken(authToken)) {
                return Mono.just(new UsernamePasswordAuthenticationToken(authToken, authToken, new ArrayList<SimpleGrantedAuthority>()))
                        .map(SecurityContextImpl::new);
            }
            if (authToken != null && !authToken.trim().equals("")) {
                var token = new UsernamePasswordAuthenticationToken(authToken, authToken);
                return manager.authenticate(token).map(SecurityContextImpl::new);
            }
            return Mono.empty();
        }
    }

    private static class AuthenticationManager implements ReactiveAuthenticationManager {
        @Override
        public Mono<Authentication> authenticate(Authentication authentication) {
            var token = authentication.getCredentials().toString();
            var auth = new UsernamePasswordAuthenticationToken(token, token, new ArrayList<SimpleGrantedAuthority>());
            return Mono.just(auth);
        }
    }
}
