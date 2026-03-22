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

    private static final long ACCESS_EXPIRE_MILLIS = 1000L * 60 * 60 * 2;
    private static final long REFRESH_EXPIRE_MILLIS = 1000L * 60 * 60 * 24 * 7;

    public static String generateAccessToken(String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .claim("type", "access")
                .issuedAt(new Date(now))
                .expiration(new Date(now + ACCESS_EXPIRE_MILLIS))
                .signWith(KEY)
                .compact();
    }

    public static String generateRefreshToken(String username, Integer tokenVersion) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .claim("type", "refresh")
                .claim("tokenVersion", tokenVersion == null ? 0 : tokenVersion)
                .issuedAt(new Date(now))
                .expiration(new Date(now + REFRESH_EXPIRE_MILLIS))
                .signWith(KEY)
                .compact();
    }

    private static Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static String parseUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public static Integer parseTokenVersion(String token) {
        Object value = parseClaims(token).get("tokenVersion");
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    public static boolean isAccessTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return "access".equals(claims.get("type"));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isRefreshTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return "refresh".equals(claims.get("type"));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isValid(String token) {
        return isAccessTokenValid(token);
    }
}
