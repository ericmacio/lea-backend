package emk.ai.chat.filter;

import emk.ai.chat.util.Utils;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(0)
public class ApiKeyFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);
    private String expectedApiKey;

    @Value("${chat.api-key-header:X-Chat-Api-Key}")
    private String apiKeyHeader;

    @Value("${api.secret.key}")
    private String apiSecretKey;

    @PostConstruct
    private void init() {
        this.expectedApiKey = getApiKey();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // To be applied only to /api/chat
        if (!"/api/chat".equals(httpRequest.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        // Allows preflight CORS request
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // get API key from header
        String providedKey = httpRequest.getHeader(apiKeyHeader);

        if (providedKey == null || !providedKey.equals(expectedApiKey)) {
            log.error("API key is not valid. UnAuthorized access");
            Utils.writeJsonError((HttpServletResponse) response,
                    401,
                    "Désolé, vous n'êtes pas authorisé à utiliser ce service");
        } else {
            log.info("API key is valid");
        }

        chain.doFilter(request, response);
    }

    private String getApiKey() {
        return apiSecretKey;
    }
}
