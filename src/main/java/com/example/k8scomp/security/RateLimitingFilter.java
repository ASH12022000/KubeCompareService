package com.example.k8scomp.security;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitingFilter implements Filter {

    private final LoadingCache<String, Bucket> buckets = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build(new CacheLoader<>() {
                @Override
                public Bucket load(String key) {
                    return Bucket4j.builder()
                            .addLimit(Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1))))
                            .build();
                }
            });

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String ip = httpRequest.getRemoteAddr();
        
        try {
            Bucket bucket = buckets.get(ip);
            if (bucket.tryConsume(1)) {
                chain.doFilter(request, response);
            } else {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setStatus(429);
                httpResponse.getWriter().write("Too many requests");
            }
        } catch (ExecutionException e) {
            chain.doFilter(request, response);
        }
    }
}
