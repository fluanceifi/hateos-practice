package com.example.smu_club.auth.service;


import com.example.smu_club.auth.dto.JwtTokenResponse;
import com.example.smu_club.auth.dto.ReissueRequest;
import com.example.smu_club.auth.jwt.JwtTokenProvider;
import com.example.smu_club.domain.Member;
import com.example.smu_club.exception.custom.ExpiredTokenException;
import com.example.smu_club.exception.custom.InvalidRefreshTokenException;
import com.example.smu_club.exception.custom.InvalidTokenException;
import com.example.smu_club.exception.custom.MemberNotFoundException;
import com.example.smu_club.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public JwtTokenResponse generateTokenAndUpdateRefreshToken(String studentId){

        Member member = memberRepository.findByStudentId(studentId)
                .orElseThrow(() -> new MemberNotFoundException("가입되지 않은 회원입니다"));

        JwtTokenResponse tokenResponse = jwtTokenProvider.generateToken(member);
        String refreshToken = tokenResponse.getRefreshToken();
        member.updateRefreshToken(refreshToken);

        return tokenResponse;
    }

    @Transactional
    public JwtTokenResponse reissueTokens(ReissueRequest reissueRequest){

        String refreshToken = reissueRequest.getRefreshToken();

        try {
            jwtTokenProvider.validateToken(refreshToken);
        } catch (ExpiredTokenException | InvalidTokenException e) {
            throw new InvalidRefreshTokenException("[RefreshToken] 만료되었거나 유효하지 않은 토큰입니다. 다시 로그인해주세요.");
        }

        Member member = memberRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new InvalidRefreshTokenException("[서버존재 X] 유효하지 않은 Refresh Token 입니다"));

        JwtTokenResponse tokenResponse = jwtTokenProvider.generateToken(member);
        member.updateRefreshToken(tokenResponse.getRefreshToken());

        return tokenResponse;

    }

}
