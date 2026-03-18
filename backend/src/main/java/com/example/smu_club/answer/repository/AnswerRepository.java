package com.example.smu_club.answer.repository;

import com.example.smu_club.domain.Answer;
import com.example.smu_club.domain.Club;
import com.example.smu_club.domain.Member;
import com.example.smu_club.domain.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    @Query("SELECT a FROM Answer a WHERE a.member = :member AND a.question IN :questions")
    List<Answer> findByMemberAndQuestions(@Param("member")Member member, @Param("questions") List<Question> questions);


    //1. 스프링에선 JPA @Query가 발생시 기본적으로 SELECT로 간주 함.
    //2. 그래서 @Modifying이 UPDATE/DELETE/INSERT 인걸 알려줌 -> 결과 매핑하지말고 몇개 성공했는지만 (int)알려주던가 (void)말던가 맘대로 해.
    //3. 근데 JPA에선 기본적인 쿼리 메소드로 UPDATE/DELETE 할 때 SELECT로 1차 캐시에 로드 후 DELETE를 진행함.
    //4. 이런 비효율을 막기위해 @Modifying은 엔티티 단위 삭제(remove)”가 아니라 “JPQL 단위 삭제(delete 쿼리)”로 수행함
    //   -> 엔티티 매니저를 거치지 않아서 1차 캐시를 무시하고 쿼리가 실행됨.
    //5. 그래서 만약에 1차 캐시에 로드된 객체가 변경 되는 경우를 위해 (clearAutomatically = ture)로 캐시를 비워 정합성 맞추는 기능도 존재함.
    @Modifying
    @Query("DELETE FROM Answer a WHERE a.member = :member AND a.question.club.id = :clubId")
    void deleteByMemberAndClubId(Member member, Long clubId);

    @Query("SELECT a FROM Answer a " +
            "JOIN FETCH a.question q " +
            "WHERE a.member = :member AND q.club = :club " +
            "ORDER BY q.orderNum ASC")
    List<Answer> findByMemberAndClubWithQuestions(
            @Param("member") Member member,
            @Param("club") Club club
    );

    @Query("SELECT a FROM Answer a JOIN FETCH a.question q " +
            "WHERE a.member = :member " +
            "AND q.id IN :questionIds " +
            "AND q.club.id = :clubId ")
    List<Answer> findAnswerForUpdateWithClubId(
            @Param("member") Member member,
            @Param("questionIds") Set<Long> questionIds,
            @Param("clubId") Long clubId);


    @Query("DELETE FROM Answer a WHERE a.member = :member AND a.question IN :questions")
    void deleteByMemberAndQuestionId(Member member, List<Question> questions);

    @Query("SELECT a.fileKey FROM Answer a WHERE a.fileKey IS NOT NULL")
    List<String> findAllFileKeys();
}
