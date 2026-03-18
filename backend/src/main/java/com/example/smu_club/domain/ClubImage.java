package com.example.smu_club.domain;


import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "club_image")
public class ClubImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "club_image_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(nullable = false, length = 512)
    private String imageFileKey;

    @Column(nullable = false)
    private int displayOrder;

    @Builder
    public ClubImage(Club club, String imageFileKey, int displayOrder) {
        this.club = club;
        this.imageFileKey = imageFileKey;
        this.displayOrder = displayOrder;
    }

}
