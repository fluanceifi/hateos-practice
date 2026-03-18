package com.example.smu_club.club.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClubInfoRequest {

    private List<String> uploadedImageFileNames;
    private String name;
    private String title;
    private String president;
    private String contact;
    private LocalDate recruitingEnd;
    private String clubRoom;
    private String description;

}
