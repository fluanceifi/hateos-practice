package com.example.smu_club.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long id;

    //하나의 클럽은 여러개의 질문을 가질 수 있다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id")
    private Club club;

    private String content;

    @Column(name = "question_content_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private QuestionContentType questionContentType;

    //질문 순서
    @Column(name = "order_num")
    private int orderNum;

    @Builder
    public Question(Club club, String content, QuestionContentType questionContentType, int orderNum) {
        this.club = club;
        this.content = content;
        this.questionContentType = questionContentType;
        this.orderNum = orderNum;
    }
}
