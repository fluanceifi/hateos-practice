package com.example.smu_club.common.fileMetaData;


import com.example.smu_club.domain.FileMetaData;
import com.example.smu_club.domain.FileStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class FileMetaDataService {

    private final FileMetaDataRepository fileMetaDataRepository;

    public void savePending(String fileKey) {
        FileMetaData fileMetaData = FileMetaData.builder()
                .fileKey(fileKey)
                .status(FileStatus.PENDING)
                .createAt(LocalDateTime.now())
                .build();
        fileMetaDataRepository.save(fileMetaData);
    }

    // 상태 변경 (ACTIVE 또는 DELETED 로 변경 시 사용)
    public void updateStatus(List<String> fileKeys, FileStatus status) {

        if (fileKeys == null || fileKeys.isEmpty()) {
            return;
        }

        List<FileMetaData> files = fileMetaDataRepository.findAllByFileKeyIn(fileKeys);
        files.forEach(file -> file.updateStatus(status));
    }
}
