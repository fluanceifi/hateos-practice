package com.example.smu_club.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FileMetaData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String fileKey;

    @Enumerated(EnumType.STRING)
    private FileStatus status;

    private LocalDateTime createAt;

    public void updateStatus(FileStatus status) {
        this.status = status;
    }
}
