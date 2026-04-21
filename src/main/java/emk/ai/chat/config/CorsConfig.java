package emk.ai.chat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${chat.allowed-origin}")
    private String allowedOrigin;

    // Phase 1
    @Value("${chat.allowed-origin-phase1:}")
    private String allowedOriginPhase1;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        if (allowedOriginPhase1 != null && !allowedOriginPhase1.isEmpty()) {
            config.setAllowedOrigins(List.of(allowedOrigin, allowedOriginPhase1));
        } else {
            config.setAllowedOrigins(List.of(allowedOrigin));
        }

        config.setAllowedMethods(List.of("POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-Chat-Api-Key"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        // Make sure CORS politic information is sent first
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
