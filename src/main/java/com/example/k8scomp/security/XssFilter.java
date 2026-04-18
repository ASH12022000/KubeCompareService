package com.example.k8scomp.security;

import jakarta.servlet.*;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class XssFilter implements Filter {

    private final PolicyFactory policy = Sanitizers.FORMATTING.and(Sanitizers.LINKS);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        chain.doFilter(request, response);
    }
}
