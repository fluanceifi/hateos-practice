package com.example.smu_club.auth.service;
import com.example.smu_club.auth.dto.*;
import com.example.smu_club.auth.external.UnivApiClient;
import com.example.smu_club.auth.jwt.JwtTokenProvider;
import com.example.smu_club.domain.Member;
import com.example.smu_club.domain.Role;
import com.example.smu_club.exception.custom.*;
import com.example.smu_club.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UnivApiClient univApiClient;
    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenService tokenService;


    /**
     * 로그인 로직 처리 메서드
     */
    public JwtTokenResponse login(LoginRequest loginRequest) {

        // 1. 외부 API 호을 트랜잭션 밖에서 호출
        UnivUserInfoResponse userInfo = univApiClient.authenticate(
                loginRequest.getStudentId(), loginRequest.getPassword()
        );

        if (userInfo == null) {
            throw new LoginFailedException("학번 또는 비밀번호가 일치하지 않습니다");
        }

        // DB 작업이 필요한 부분을 별도의 트랜잭션으로 호출
        return tokenService.generateTokenAndUpdateRefreshToken(userInfo.getUsername());

    }

    public void signUp(SignupRequest signupRequest){

        // 1. 학교 API 를 통해서 인증
        UnivUserInfoResponse userInfo = univApiClient.authenticate(
                signupRequest.getStudentId(), signupRequest.getPassword()
        );

        if (userInfo == null) {
                throw new LoginFailedException("학번 또는 비밀번호가 일치하지 않아 학교 인증에 실패했습니다.");
        }

        // 2. 중복 확인
        memberRepository.findByStudentId(userInfo.getUsername())
                .ifPresent(member -> {
                    throw new MemberAlreadyExistsException("이미 가입된 학번입니다.");
                });

        // 3. Member 객체 생성 -
        Member newMember = Member.builder()
                .studentId(userInfo.getUsername())
                .name(userInfo.getName())
                .email(userInfo.getEmail())
                .department(userInfo.getDepartment())
                .role(Role.MEMBER)
                .phoneNumber(signupRequest.getPhoneNumber())
                .build();

        // 4. 저장 DB에 newMember 저장
        memberRepository.save(newMember);
    }

    // 로그아웃 시 사용자 refreshToken 을 null처리
    @Transactional
    public void logout(String studentId) {

        Member member = memberRepository.findByStudentId(studentId)
                .orElseThrow(() -> new MemberNotFoundException("해당 사용자를 찾을 수 없습니다."));

        member.clearRefreshToken();
    }
}
