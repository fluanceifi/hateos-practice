package com.example.smu_club.common.fileMetaData;

import com.example.smu_club.domain.FileMetaData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileMetaDataRepository extends JpaRepository<FileMetaData, Long> {

    List<FileMetaData> findAllByFileKeyIn(List<String> fileKeys);


}
