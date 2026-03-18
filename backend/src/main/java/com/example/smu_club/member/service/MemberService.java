package com.example.smu_club.member.service;

import com.example.smu_club.answer.dto.AnswerResponseDto;
import com.example.smu_club.answer.repository.AnswerRepository;
import com.example.smu_club.club.repository.ClubMemberRepository;
import com.example.smu_club.club.repository.ClubRepository;
import com.example.smu_club.domain.*;
import com.example.smu_club.exception.custom.*;
import com.example.smu_club.member.dto.*;
import com.example.smu_club.member.repository.MemberRepository;
import com.example.smu_club.question.repository.QuestionRepository;
import com.example.smu_club.util.oci.OciStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

import static com.example.smu_club.domain.QuestionContentType.FILE;
import static com.example.smu_club.domain.QuestionContentType.TEXT;

@Slf4j
@Service
@Transactional(readOnly = true) //JPA 모든 데이터/로직 변경은 가급적 트랜잭션에서 실행 되어야함. -> 그래야 LAZY 로딩 같은 기능이 가능함
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final ClubRepository clubRepository;
    private final OciStorageService ociStorageService;

    public MemberNameResponseDto MyName(String studentId) {
        Member member = memberRepository.findByStudentId(studentId)
                .orElseThrow(() -> new MemberNotFoundException("학번: " + studentId + "에 해당하는 회원을 찾을 수 없습니다."));
        return new MemberNameResponseDto(member.getName());
    }

    //지원 목록 전달
    public List<ApplicationListResponseDto> findApplications(String studentId) {
        //여기서 나올 수 있는 예외가 있을까? -> 토큰에서 넘겨준 studentId가 잘못된 경우 밖에 없을 것이다.

        //1. 내가 가진 정보는 학번 뿐이다. -> 넘겨줄 데이터는 클럽의 아이디와 클럽의 이름
        // [ClubMember에서 Member엔티티 중 학번과 일치하는 Member 엔티티만 조회함.]
        List<ClubMember> clubMember = clubMemberRepository.findAllByStudentIdAndRoleNot(studentId, ClubRole.OWNER);
        //3.필요한 ClubMember만 List로 저장되었으니 Dto로 변환한다.
        //중요한점은 아무것도 없는 상태여도 인 List여도 상관없다는 것이다.
        //데이터가 없을 경우 ResponseEntity.ok에 감싸진 ApiResponseDto의 success 메소드에서 메세지가 구분된다.
        return clubMember.stream().map(
                cm -> new ApplicationListResponseDto(
                        cm.getClub().getId(),
                        cm.getClub().getName()
                )).toList();
    }

    public ApplicationResultResponseDto findResult(String studentId, Long clubId) {
        ClubMember clubMember = clubMemberRepository.findAllWithMemberAndClubByStudentIdAndClubId(studentId, clubId);
        if(clubMember == null) throw new ClubMemberNotFoundException("해당 클럽에 지원하지 않았습니다.");

        return new ApplicationResultResponseDto(
                clubMember.getMember().getId(),
                clubMember.getClub().getId(),
                clubMember.getMember().getName(),
                clubMember.getClub().getName(),
                clubMember.getStatus()
        );

    }

    public UpdateApplicationResponseDto showApplication(Long clubId, String studentId) {
        /** 순서
         *  1. studentId로 Member 객체를 생성하여 내 정보를 가져와야 됨.
         *  2. clubId로 Question 객체를 List로 생성한다. -> orderNum으로 정렬한 생태로 가져온다.
         *  3. Member 객체와 Question 객체를 이용하여 Answer객체를 List로 생성한다. (member_id, question_id) → UNIQUE  제약 조건
         *  4. Map<Long, Answer> answerMap = answers.stream().collect(Collectors.toMap(answer -> answer.getQuestion().getId(), answer -> answer)
         *     를 이용하여 조회 성능을 향상 시키자.
         *  5. EditApplicationResponseDto 안에 AnswerResponseDto 리스트를 채우기 위해 stream().map().collect()로 특정 필드를 채워 리스트를 생성한다.
         *  5. EditApplicationResponseDto에 맞게 Member의 특정 필드와 와 AnswerResponseDto 리스트 값을 Mapping 후 반환한다.
         */
        Member member = memberRepository.findByStudentId(studentId)
                .orElseThrow(() -> new MemberNotFoundException("학번: " + studentId + "에 해당하는 회원을 찾을 수 없습니다."));

        clubRepository.findById(clubId)
                .orElseThrow(() -> new ClubNotFoundException("동아리 ID: " + clubId + "에 해당하는 동아리를 찾을 수 없습니다."));


        List<Question> questions = questionRepository.findByClubIdOrderByOrderNumAsc(clubId);
        if(questions.isEmpty()) throw new QuestionNotFoundException("동아리 ID: " + clubId + "에 해당하는 질문들을 찾을 수 없습니다.");

        List<Answer> answers = answerRepository.findByMemberAndQuestions(member, questions);


        //질문 타입이 TEXT이면 AnswerContentMap에 저장한다.
        //Key: QuestionId, Value: AnswerContent
        //filter()는 스트림의 요소들 중에서 특정 조건을 만족하는 것들만 남겨주는 중간 다리 역할
        Map<Long, String> answerContentsMap = answers.stream()
                .filter(answer -> answer.getQuestion().getQuestionContentType() == TEXT)
                .collect(Collectors.toMap(
                        answer -> answer.getQuestion().getId(),
                        Answer::getAnswerContent
                ));

        //질문 타입이 FILE인 Answer를 찾는다.
        Optional<Answer> fileTypeAnswer = answers.stream()
                .filter(answer -> answer.getQuestion().getQuestionContentType() == FILE)
                .findFirst();

        //fileKey 추출 (없다면 null)
        String fileUrl = fileTypeAnswer
                .map(Answer::getFileKey)
                .map(ociStorageService::createFinalOciUrl)
                .orElse(null);

        List<AnswerResponseDto> answerResponseDto = questions.stream()
                .filter(q -> q.getQuestionContentType() == TEXT)
                .map(q -> new AnswerResponseDto(
                        q.getId(),
                        q.getOrderNum(),
                        q.getContent(),
                        answerContentsMap.getOrDefault(q.getId(), "")
                ))
                .toList();

        return new UpdateApplicationResponseDto(
                member.getId(),
                member.getStudentId(),
                member.getDepartment(),
                member.getName(),
                member.getPhoneNumber(),
                answerResponseDto,
                fileUrl
        );
    }

    public UpdateMyInfoResponseDto showMyInformation(String studentId) {
        return memberRepository.findEditMyInfoByStudentId(studentId)
                .orElseThrow(() -> new MemberNotFoundException("학번: " + studentId + "에 해당하는 회원을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = false)
    public void updateMyEmail(String studentId, UpdateMyEmailRequestDto requestDto) {
        Member member = memberRepository.findByStudentId(studentId)
                .orElseThrow(() -> new MemberNotFoundException("학번: " + studentId + "에 해당하는 회원을 찾을 수 없습니다."));


        //@Transactional로 알아 더티 체킹하여 메소드 끝날 때 쿼리 발생
        if(requestDto.getNewEmail() != null && !requestDto.getNewEmail().isEmpty())
            member.setEmail(requestDto.getNewEmail());
    }

    @Transactional(readOnly = false)
    public void updateMyPhoneNumber(String studentId, UpdateMyPhoneNumberRequestDto requestDto) {

        Member member = memberRepository.findByStudentId(studentId)
                .orElseThrow(() -> new MemberNotFoundException("학번: " + studentId + "에 해당하는 회원을 찾을 수 없습니다."));

        //@Transactional로 알아 더티 체킹하여 메소드 끝날 때 쿼리 발생
        if(requestDto.getNewPhoneNumber() != null && !requestDto.getNewPhoneNumber().isEmpty())
            member.setPhoneNumber(requestDto.getNewPhoneNumber());
    }
    @Transactional(readOnly = false)
    public void deleteApplication(String studentId, Long clubId) {
        Member member = memberRepository.findByStudentId(studentId)
                .orElseThrow(() -> new MemberNotFoundException("학번: " + studentId + "에 해당하는 회원을 찾을 수 없습니다."));

        answerRepository.deleteByMemberAndClubId(member, clubId);

        //status가 PENDING 이여도 동아리 멤버에서 탈퇴 시킨다.
        clubMemberRepository.deleteByClubIdAndMemberId(clubId, member.getId());


    }

    @Transactional(readOnly = false)
    public void updateApplication(Long clubId, String studentId, UpdateApplicationRequestDto requestDto) {

        //1. studentId로 Member 객체 조회
        Member member = memberRepository.findByStudentId(studentId)
                .orElseThrow(() -> new MemberNotFoundException("학번: " + studentId + "에 해당하는 회원을 찾을 수 없습니다."));

        //2. UpdateAnswerRequestDto 리스트를 Map<Long, String>으로 변환
        Map<Long, String> answerContentsMap = requestDto.getAnswers().stream()
                .collect(Collectors.toMap(
                        UpdateAnswerRequestDto::getQuestionId,
                        answers -> answers.getAnswerContent() == null ? "" : answers.getAnswerContent()
                ));

        //3.
        List<Answer> answerToUpdate = answerRepository.findAnswerForUpdateWithClubId
                (
                member,
                answerContentsMap.keySet(),
                clubId //검증용
        );

        for(Answer target : answerToUpdate) {
            String newAnswerContent = answerContentsMap.get(target.getQuestion().getId());
            //답변 업데이트 수행
            target.update(newAnswerContent, requestDto.getFileKey());
        }


        //Dirty checking -> batch size(50) -> 네트워크 통신 2번만으로 벌크 연산(업데이트) 해결
    }
}
