package emk.ai.chat.filter;

import emk.ai.chat.util.Utils;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(2)
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    @Value("${chat.rate-limit.requests-per-hour}")
    private int requestPerHourLimit;

    @Value("${chat.rate-limit.requests-per-minute}")
    private int requestPerMinuteLimit;

    @Value("${chat.rate-limit.global.requests-per-hour}")
    private int globalRequestPerHourLimit;

    @Value("${chat.rate-limit.global.requests-per-minute}")
    private int globalRequestPerMinuteLimit;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private volatile Bucket globalBucket;

    // Create per-IP rate limiting bucket
    private Bucket createBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestPerHourLimit)
                        .refillGreedy(requestPerHourLimit, Duration.ofHours(1))
                        .build())
                .addLimit(Bandwidth.builder()
                        .capacity(requestPerMinuteLimit)
                        .refillGreedy(requestPerMinuteLimit, Duration.ofMinutes(1)) // 5 msg/minute (anti-burst)
                        .build())
                .build();
    }

    // Lazy-init global bucket (shared across all IPs)
    private Bucket getGlobalBucket() {
        if (globalBucket == null) {
            synchronized (this) {
                if (globalBucket == null) {
                    globalBucket = Bucket.builder()
                            .addLimit(Bandwidth.builder()
                                    .capacity(globalRequestPerHourLimit)
                                    .refillGreedy(globalRequestPerHourLimit, Duration.ofHours(1))
                                    .build())
                            .addLimit(Bandwidth.builder()
                                    .capacity(globalRequestPerMinuteLimit)
                                    .refillGreedy(globalRequestPerMinuteLimit, Duration.ofMinutes(1))
                                    .build())
                            .build();
                }
            }
        }
        return globalBucket;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // Apply only to /api/chat
        if (!"/api/chat".equals(httpRequest.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        // Allows preflight CORS request
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // Get remote address
        String ip = Utils.getClientIp(httpRequest);
        log.info("ip: {}", ip);
        // Get bucket from Map. Build bucket if ip key is absent from the Map (if !buckets.containsKey(ip))
        Bucket bucket = buckets.computeIfAbsent(ip, k -> createBucket());

        // Check global rate limit first
        if (!getGlobalBucket().tryConsume(1)) {
            log.error("ERROR: global rate limit exceeded");
            Utils.writeJsonError((HttpServletResponse) response,
                    429,
                    "Le service est temporairement surchargé. Réessayez dans quelques minutes.");
            return;
        }

        // Then check per-IP rate limit
        if (!bucket.tryConsume(1)) {
            log.error("ERROR: max number of messages exceeded for IP {}", ip);
            Utils.writeJsonError((HttpServletResponse) response,
                    429,
                    "Désolé, vous avez atteint le nombre maximal de messages autorisé. Réessayez dans un moment.");
        } else {
            chain.doFilter(request, response);
        }
    }
}
