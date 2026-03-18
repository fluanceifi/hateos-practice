    package com.example.smu_club.auth.jwt;


    import com.example.smu_club.auth.dto.JwtTokenResponse;
    import com.example.smu_club.domain.Member;
    import com.example.smu_club.exception.custom.ExpiredTokenException;
    import com.example.smu_club.exception.custom.InvalidTokenException;
    import io.jsonwebtoken.*;
    import io.jsonwebtoken.io.Decoders;
    import io.jsonwebtoken.security.Keys;
    import jakarta.annotation.PostConstruct;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
    import org.springframework.security.core.Authentication;
    import org.springframework.security.core.GrantedAuthority;
    import org.springframework.security.core.authority.SimpleGrantedAuthority;
    import org.springframework.security.core.userdetails.User;
    import org.springframework.security.core.userdetails.UserDetails;
    import org.springframework.stereotype.Component;

    import java.security.Key;
    import java.util.Collection;
    import java.util.Collections;
    import java.util.Date;

    @Slf4j
    @Component
    public class JwtTokenProvider {

        private static final String AUTHORITIES = "auth";

        private final Key key;
        private final long accessTokenValidityInMilliseconds;
        private final long refreshTokenValidityInMilliseconds;

        public JwtTokenProvider(
                @Value("${jwt.secret}") String secretKey,
                @Value("${jwt.access-token-validity-in-seconds}") long accessTokenValidityInSeconds,
                @Value("${jwt.refresh-token-validity-in-seconds}") long refreshTokenValidityInSeconds) {

            byte[] keyBytes = Decoders.BASE64.decode(secretKey);
            this.key = Keys.hmacShaKeyFor(keyBytes);

            this.accessTokenValidityInMilliseconds = accessTokenValidityInSeconds * 1000;
            this.refreshTokenValidityInMilliseconds = refreshTokenValidityInSeconds * 1000;
        }


        public void validateToken(String refreshToken) {
            parseClaims(refreshToken);
        }

        public JwtTokenResponse generateToken(Member member) {
            String authorities = member.getRole().getKey();

            long now = (new Date()).getTime();

            // access Token 생성
            Date accessTokenExpiresIn = new Date(now + accessTokenValidityInMilliseconds);

            String accessToken = Jwts.builder()
                    .setSubject(member.getStudentId())
                    .claim(AUTHORITIES, authorities)
                    .setIssuedAt(new Date(now))
                    .setExpiration(accessTokenExpiresIn)
                    .signWith(key, SignatureAlgorithm.HS256)
                    .compact();


            // refreshToken 생성
            Date refreshTokenExpiresIn = new Date(now + refreshTokenValidityInMilliseconds);
            String refreshToken = Jwts.builder()
                    .setExpiration(refreshTokenExpiresIn)
                    .signWith(key, SignatureAlgorithm.HS256)
                    .compact();

            return JwtTokenResponse.builder()
                    .grantType("Bearer")
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .build();
        }


        public Authentication getAuthentication(String accessToken) {

            Claims claims = parseClaims(accessToken);

            if(claims.get(AUTHORITIES) == null) {
                throw new InvalidTokenException("권한 정보가 없는 토큰입니다.");
            }

            Collection<? extends GrantedAuthority> authorities =
                    Collections.singletonList(new SimpleGrantedAuthority(claims.get(AUTHORITIES).toString()));

            UserDetails principal = new User(claims.getSubject(), "", authorities);

            return new UsernamePasswordAuthenticationToken(principal,"", authorities);
        }

        // Key로 토큰 검증
        private Claims parseClaims(String token) {
            try {
                return Jwts.parserBuilder()
                        .setSigningKey(key)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();
            } catch (ExpiredJwtException e) {
                log.warn("만료된 토큰 확인 ");
                throw new ExpiredTokenException("만료된 토큰입니다.");
            } catch (JwtException | IllegalArgumentException e) {
                throw new InvalidTokenException("유효하지 않은 JWT 토큰입니다.");
            }

        }


    }
