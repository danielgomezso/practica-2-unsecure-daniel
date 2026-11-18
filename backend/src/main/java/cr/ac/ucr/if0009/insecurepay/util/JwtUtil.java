package cr.ac.ucr.if0009.insecurepay.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtUtil(@Value("${jwt.secret:}") String secret, @Value("${jwt.expiration-ms}") long expirationMs) {
        // Sin secreto configurado, cada arranque usa una clave aleatoria para el laboratorio local.
        this.signingKey = secret.isBlank() ? Jwts.SIG.HS256.key().build()
                : Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    private SecretKey getSigningKey() {
        return signingKey;
    }

    public String generateToken(String username, String rol, Long usuarioId) {
        return Jwts.builder()
                .subject(username)
                .claim("rol", rol)
                .claim("usuarioId", usuarioId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
