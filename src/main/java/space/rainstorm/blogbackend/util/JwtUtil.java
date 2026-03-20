package space.rainstorm.blogbackend.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtUtil {

    private static final String SECRET = "rainstorm-blog-secret-key-123456789012345";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    public static String generateToken(String username) {
        long now = System.currentTimeMillis();
        long expireTime = now + 1000L * 60 * 60 * 24;

        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date(now))
                .expiration(new Date(expireTime))
                .signWith(KEY)
                .compact();
    }

    public static String parseUsername(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    public static boolean isValid(String token) {
        try {
            parseUsername(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}