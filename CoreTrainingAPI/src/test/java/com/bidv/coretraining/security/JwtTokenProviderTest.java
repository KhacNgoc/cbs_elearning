package com.bidv.coretraining.security;

import com.bidv.coretraining.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {
    private static final String SECRET = "test-signing-secret-at-least-thirty-two-bytes-long";
    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);

    private String token(JwtTokenProvider signer) {
        var user = User.builder().id(42L).username("learner").tokenVersion(3L).build();
        return signer.generateToken(new UsernamePasswordAuthenticationToken(new CustomUserDetails(user), null));
    }

    @Test void validTokenPreservesUserIdAndVersionClaims() {
        var jwt = token(provider);
        assertThat(provider.validateToken(jwt)).isTrue();
        assertThat(provider.getUserIdFromJWT(jwt)).isEqualTo(42L);
        var claims = io.jsonwebtoken.Jwts.parserBuilder()
                .setSigningKey(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes()))
                .build().parseClaimsJws(jwt).getBody();
        assertThat(claims.get("ver", Number.class).longValue()).isEqualTo(3L);
    }

    @Test void wrongSignatureIsRejectedWithoutThrowing() {
        var otherSigner = new JwtTokenProvider("different-test-signing-key-at-least-thirty-two-bytes", 60_000);
        assertThat(provider.validateToken(token(otherSigner))).isFalse();
    }

    @Test void expiredTokenIsRejected() {
        assertThat(provider.validateToken(token(new JwtTokenProvider(SECRET, -60_000)))).isFalse();
    }

    @Test void malformedAndEmptyTokensAreRejected() {
        assertThat(provider.validateToken("not-a-jwt")).isFalse();
        assertThat(provider.validateToken("")).isFalse();
    }
}
