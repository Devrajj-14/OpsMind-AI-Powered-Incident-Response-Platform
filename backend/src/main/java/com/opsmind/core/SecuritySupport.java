package com.opsmind.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.domain.Types.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

final class Hashing {
    private Hashing() {}
    static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    static String randomToken() { return Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)); }
}

record AuthPrincipal(UUID id, String email, String displayName, Role role) {}

@Component
class JwtService {
    private final ObjectMapper mapper;
    private final byte[] secret;
    private final long accessSeconds;
    JwtService(ObjectMapper mapper, @Value("${opsmind.jwt-secret}") String secret, @Value("${opsmind.access-token-seconds}") long accessSeconds) {
        this.mapper=mapper; this.secret=secret.getBytes(StandardCharsets.UTF_8); this.accessSeconds=accessSeconds;
    }
    String create(User user) {
        try {
            String header=encode(mapper.writeValueAsBytes(Map.of("alg","HS256","typ","JWT")));
            long now=Instant.now().getEpochSecond();
            String payload=encode(mapper.writeValueAsBytes(Map.of("sub",user.id.toString(),"email",user.email,"name",user.displayName,"role",user.role.name(),"iat",now,"exp",now+accessSeconds,"iss","opsmind")));
            String unsigned=header+"."+payload;
            return unsigned+"."+encode(sign(unsigned));
        } catch (Exception e) { throw new IllegalStateException("Could not issue token",e); }
    }
    Optional<AuthPrincipal> verify(String token) {
        try {
            String[] parts=token.split("\\."); if(parts.length!=3) return Optional.empty();
            String unsigned=parts[0]+"."+parts[1];
            if(!MessageDigest.isEqual(sign(unsigned), Base64.getUrlDecoder().decode(parts[2]))) return Optional.empty();
            Map<String,Object> p=mapper.readValue(Base64.getUrlDecoder().decode(parts[1]),new TypeReference<>(){});
            if(((Number)p.get("exp")).longValue()<Instant.now().getEpochSecond() || !"opsmind".equals(p.get("iss"))) return Optional.empty();
            return Optional.of(new AuthPrincipal(UUID.fromString((String)p.get("sub")),(String)p.get("email"),(String)p.get("name"),Role.valueOf((String)p.get("role"))));
        } catch(Exception e) { return Optional.empty(); }
    }
    private byte[] sign(String input) throws Exception { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret,"HmacSHA256")); return mac.doFinal(input.getBytes(StandardCharsets.UTF_8)); }
    private String encode(byte[] b) { return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
}

@Component
class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    JwtFilter(JwtService jwt) { this.jwt=jwt; }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String header=req.getHeader(HttpHeaders.AUTHORIZATION);
        if(header!=null && header.startsWith("Bearer ")) jwt.verify(header.substring(7)).ifPresent(p -> {
            var auth=new UsernamePasswordAuthenticationToken(p,null,List.of(new SimpleGrantedAuthority("ROLE_"+p.role().name())));
            SecurityContextHolder.getContext().setAuthentication(auth);
        });
        chain.doFilter(req,res);
    }
}

@Configuration @EnableMethodSecurity
class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean SecurityFilterChain filterChain(HttpSecurity http, JwtFilter jwt, CorsConfigurationSource cors) throws Exception {
        return http.csrf(c->c.disable()).cors(c->c.configurationSource(cors)).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.requestMatchers("/api/v1/auth/**","/api/v1/ingestion/**","/actuator/health/**","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll().anyRequest().authenticated())
            .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,x)->{res.setStatus(401);res.setContentType("application/json");res.getWriter().write("{\"title\":\"Authentication required\",\"status\":401}");}))
            .build();
    }
    @Bean CorsConfigurationSource cors(@Value("${opsmind.frontend-origin}") String origin) {
        var c=new CorsConfiguration(); c.setAllowedOrigins(List.of(origin)); c.setAllowedMethods(List.of("GET","POST","PATCH","DELETE","OPTIONS")); c.setAllowedHeaders(List.of("*")); c.setAllowCredentials(true);
        var source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**",c); return source;
    }
}
