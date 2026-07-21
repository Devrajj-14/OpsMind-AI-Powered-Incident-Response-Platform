package com.opsmind.core;

import com.opsmind.domain.Types.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/auth")
class AuthController {
    private final UserRepository users; private final RefreshTokenRepository tokens; private final PasswordEncoder passwords; private final JwtService jwt; private final long refreshSeconds;
    AuthController(UserRepository users, RefreshTokenRepository tokens, PasswordEncoder passwords, JwtService jwt, @Value("${opsmind.refresh-token-seconds}") long refreshSeconds) {
        this.users=users; this.tokens=tokens; this.passwords=passwords; this.jwt=jwt; this.refreshSeconds=refreshSeconds;
    }
    record RegisterRequest(@Email @NotBlank String email, @Size(min=8,max=100) String password, @NotBlank @Size(max=120) String displayName) {}
    record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}
    record RefreshRequest(@NotBlank String refreshToken) {}
    record UserView(UUID id,String email,String displayName,Role role) { static UserView of(User u){return new UserView(u.id,u.email,u.displayName,u.role);} }
    record TokenView(String accessToken,String refreshToken,long expiresIn,UserView user) {}

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) @Transactional
    TokenView register(@Valid @RequestBody RegisterRequest r) {
        if(users.findByEmailIgnoreCase(r.email()).isPresent()) throw ApiException.conflict("Email is already registered");
        Role role=users.count()==0?Role.ADMIN:Role.ENGINEER;
        return issue(users.save(new User(r.email(),passwords.encode(r.password()),r.displayName(),role)));
    }
    @PostMapping("/login") @Transactional
    TokenView login(@Valid @RequestBody LoginRequest r) {
        User u=users.findByEmailIgnoreCase(r.email()).filter(x->x.active && passwords.matches(r.password(),x.passwordHash))
            .orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Invalid email or password"));
        return issue(u);
    }
    @PostMapping("/refresh") @Transactional
    TokenView refresh(@Valid @RequestBody RefreshRequest r) {
        RefreshToken old=tokens.findByTokenHashAndRevokedFalse(Hashing.sha256(r.refreshToken())).filter(t->t.expiresAt.isAfter(Instant.now()))
            .orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Refresh token is invalid or expired"));
        old.revoked=true; User u=users.findById(old.userId).filter(x->x.active).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"User is inactive")); return issue(u);
    }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    void logout(@Valid @RequestBody RefreshRequest r) { tokens.findByTokenHashAndRevokedFalse(Hashing.sha256(r.refreshToken())).ifPresent(t->t.revoked=true); }
    @GetMapping("/me") UserView me(Authentication auth) { return UserView.of(users.findById(principal(auth).id()).orElseThrow(()->ApiException.notFound("User"))); }
    private TokenView issue(User u) { String refresh=Hashing.randomToken()+Hashing.randomToken(); tokens.save(new RefreshToken(u.id,Hashing.sha256(refresh),Instant.now().plusSeconds(refreshSeconds))); return new TokenView(jwt.create(u),refresh,900,UserView.of(u)); }
    static AuthPrincipal principal(Authentication a) { if(a==null || !(a.getPrincipal() instanceof AuthPrincipal p)) throw new ApiException(HttpStatus.UNAUTHORIZED,"Authentication required"); return p; }
}
