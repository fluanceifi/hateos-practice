package com.example.smu_club.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "answer",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_answer", columnNames = {"member_id", "question_id"})
        })
public class Answer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private Question question;

    @Column(name = "answer_content")
    private String answerContent; //텍스트 답변

    @Column(name = "file_key")
    private String fileKey; // OCI Object Storage 에 업로드된 파일의 키 값

    public void update(String answerContent, String fileKey) {
        if(this.question.getQuestionContentType() == QuestionContentType.FILE) {
            this.fileKey = fileKey;
        } else {
            this.answerContent = answerContent;
        }
    }
}
