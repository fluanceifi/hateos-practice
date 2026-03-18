package com.example.fn;

import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetNamespaceRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.model.Range;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.InputStream;

public class FileValidatorFunction {

    private final ObjectStorage objectStorage;
    private final String namespaceName;

    public FileValidatorFunction() {
        // 1. 인증 설정 (Resource Principal)
        final ResourcePrincipalAuthenticationDetailsProvider provider =
                ResourcePrincipalAuthenticationDetailsProvider.builder().build();
        this.objectStorage = ObjectStorageClient.builder().build(provider);

        try {
            this.namespaceName = objectStorage.getNamespace(GetNamespaceRequest.builder().build()).getValue();
        } catch (Exception e) {
            throw new RuntimeException("Namespace 조회 실패", e);
        }
    }

    // ★ [핵심] OCI가 파일 업로드 감지 시 자동으로 이 함수를 호출하며 'OciEvent' JSON을 던져줌
    public String handleRequest(OciEvent input) {

        // 1. OCI 이벤트 데이터 파싱
        if (input == null || input.getData() == null) {
            System.err.println("Error: OCI 이벤트 데이터가 없습니다.");
            return "ERROR_NO_DATA";
        }

        // 이벤트 JSON 안에 들어있는 "파일명"과 "버킷명"을 꺼냄
        String objectName = input.getData().getResourceName();
        String bucketName = input.getData().getAdditionalDetails().getBucketName();

        System.out.println("검증 시작 (Auto Trigger): " + objectName + " in " + bucketName);

        try {
            // 2. 파일 헤더(16바이트) 읽어오기
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .namespaceName(this.namespaceName)
                    .bucketName(bucketName) // 이벤트에서 꺼낸 버킷명 사용
                    .objectName(objectName) // 이벤트에서 꺼낸 파일명 사용
                    .range(new Range(0L, 15L))
                    .build();

            GetObjectResponse response = objectStorage.getObject(getRequest);
            InputStream content = response.getInputStream();
            byte[] headerBytes = content.readAllBytes();
            content.close();

            // 3. 확장자 추출 및 매직넘버 검증
            String extension = "";
            int dotIndex = objectName.lastIndexOf(".");
            if (dotIndex >= 0) {
                extension = objectName.substring(dotIndex + 1).toLowerCase();
            }

            if (!isValidMagicNumber(extension, headerBytes)) {
                System.out.println("위변조 감지! 삭제함: " + objectName);
                deleteFile(bucketName, objectName);
                return "DETECTED_AND_DELETED";
            }

            System.out.println("검증 통과: " + objectName);
            return "VERIFIED";

        } catch (Exception e) {
            System.err.println("에러 발생: " + e.getMessage());
            e.printStackTrace();
            return "ERROR_SYSTEM";
        }
    }

    private void deleteFile(String bucket, String object) {
        try {
            objectStorage.deleteObject(DeleteObjectRequest.builder()
                    .namespaceName(this.namespaceName)
                    .bucketName(bucket)
                    .objectName(object)
                    .build());
        } catch (Exception e) {
            System.err.println("삭제 실패: " + e.getMessage());
        }
    }

    private boolean isValidMagicNumber(String ext, byte[] data) {
        if (data == null || data.length < 4) {
            return false; // 데이터가 너무 짧으면 검사 불가
        }

        // 확장자 대소문자 무시 (jpg, JPG 등)
        ext = ext.toLowerCase();

        switch (ext) {
            // ==========================
            // [이미지 파일]
            // ==========================
            case "jpg":
            case "jpeg":
                // FF D8 FF
                return data[0] == (byte) 0xFF && data[1] == (byte) 0xD8 && data[2] == (byte) 0xFF;

            case "png":
                // 89 50 4E 47 0D 0A 1A 0A
                if (data.length < 8) return false;
                return data[0] == (byte) 0x89 && data[1] == (byte) 0x50 && data[2] == (byte) 0x4E && data[3] == (byte) 0x47;

            case "webp":
                // RIFF (0~3) ... WEBP (8~11)
                // WEBP는 0-3번이 "RIFF", 8-11번이 "WEBP"여야 함
                if (data.length < 12) return false;
                return data[0] == (byte) 'R' && data[1] == (byte) 'I' && data[2] == (byte) 'F' && data[3] == (byte) 'F' &&
                        data[8] == (byte) 'W' && data[9] == (byte) 'E' && data[10] == (byte) 'B' && data[11] == (byte) 'P';

            // ==========================
            // [문서 파일]
            // ==========================
            case "pdf":
                // %PDF (25 50 44 46)
                return data[0] == (byte) 0x25 && data[1] == (byte) 0x50 && data[2] == (byte) 0x44 && data[3] == (byte) 0x46;

            case "hwp":
                // 구형 한글 파일 (OLE Compound File) - D0 CF 11 E0 A1 B1 1A E1
                if (data.length < 8) return false;
                return data[0] == (byte) 0xD0 && data[1] == (byte) 0xCF && data[2] == (byte) 0x11 && data[3] == (byte) 0xE0 &&
                        data[4] == (byte) 0xA1 && data[5] == (byte) 0xB1 && data[6] == (byte) 0x1A && data[7] == (byte) 0xE1;

            // ==========================
            // [ZIP 기반 오피스 문서들]
            // DOCX, PPTX, XLSX, HWPX는 모두 내부적으로 ZIP 포맷이라 헤더가 같습니다.
            // ==========================
            case "docx":
            case "pptx":
            case "xlsx":
            case "hwpx": // 신형 한글 파일도 ZIP 기반(OWPML)임
                // PK Zip Signature - 50 4B 03 04
                return data[0] == (byte) 0x50 && data[1] == (byte) 0x4B && data[2] == (byte) 0x03 && data[3] == (byte) 0x04;

            default:
                // 정의되지 않은 확장자는 막는다.
                return false;
        }
    }

    // ★ [중요] OCI Events JSON 구조를 받아주는 DTO (절대 바꾸지 마세요)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OciEvent {
        private OciData data;
        public OciData getData() { return data; }
        public void setData(OciData data) { this.data = data; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OciData {
        private String resourceName;
        private AdditionalDetails additionalDetails;

        public String getResourceName() { return resourceName; }
        public void setResourceName(String resourceName) { this.resourceName = resourceName; }

        public AdditionalDetails getAdditionalDetails() { return additionalDetails; }
        public void setAdditionalDetails(AdditionalDetails additionalDetails) { this.additionalDetails = additionalDetails; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdditionalDetails {
        private String bucketName;
        public String getBucketName() { return bucketName; }
        public void setBucketName(String bucketName) { this.bucketName = bucketName; }
    }
}