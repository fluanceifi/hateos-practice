package com.example.smu_club.util;

import com.example.smu_club.club.dto.ApplicantExcelDto;
import com.example.smu_club.exception.custom.ExcelException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Component
public class ExcelService {

    public byte[] createApplicantExcel(List<ApplicantExcelDto> applicants) {

        try (Workbook workbook = new XSSFWorkbook()) {
            // 1. 시트 생성
            Sheet sheet = workbook.createSheet("합격자 명단");

            // 2. 헤더(제목) 행 생성
            Row headerRow = sheet.createRow(0);
            String[] headers = {"이름", "학번", "전화번호", "이메일", "상태"};

            // 헤더 스타일 설정 (볼드체)
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // 헤더 셀 채우기
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 3. 데이터 행 채우기
            int rowNum = 1;
            for (ApplicantExcelDto applicant : applicants) {
                Row row = sheet.createRow(rowNum++);

                // 인덱스 순서: 0(이름), 1(학번), 2(전화번호), 3(이메일), 4(상태)
                row.createCell(0).setCellValue(applicant.getName());
                row.createCell(1).setCellValue(applicant.getStudentId());
                row.createCell(2).setCellValue(applicant.getPhoneNumber());
                row.createCell(3).setCellValue(applicant.getEmail());
                row.createCell(4).setCellValue(applicant.getStatus());
            }

            // 4. 컬럼 너비 자동 조절 (이쁘게 보이도록)
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // 5. 바이트 배열로 변환하여 반환
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                workbook.write(bos);
                return bos.toByteArray();
            }

        } catch (IOException e) {
            throw new ExcelException("엑셀 파일 생성 중 오류가 발생했습니다.");
        }
    }
}