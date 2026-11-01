package bank.maggy.finance;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.server.ResponseStatusException;

@Configuration
public class FinanceAuthentication implements WebMvcConfigurer {
    static final String CUSTOMER_ID = "bank.maggy.finance.authenticatedCustomerId";
    private final IdentityClient identity;

    public FinanceAuthentication(IdentityClient identity) {
        this.identity = identity;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
                if (authorization == null || !authorization.matches("(?i)^Basic [A-Za-z0-9+/]+={0,2}$")) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
                }
                request.setAttribute(CUSTOMER_ID, identity.authenticate(authorization));
                return true;
            }
        }).addPathPatterns("/api/finance/**");
    }
}
