package com.example.smu_club.util.oci;

import com.example.smu_club.domain.AllowedFileType;
import com.example.smu_club.exception.custom.NotAllowedFileType;
import com.example.smu_club.exception.custom.OciDeletionException;
import com.example.smu_club.exception.custom.OciSearchException;
import com.example.smu_club.exception.custom.OciUploadException;
import com.example.smu_club.util.PreSignedUrlResponse;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails;
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest;
import com.oracle.bmc.objectstorage.responses.CreatePreauthenticatedRequestResponse;
import com.oracle.bmc.objectstorage.responses.ListObjectsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
public class OciStorageService {

    private final ObjectStorageClient client;
    private final String namespace;
    private final String bucketName;
    private final String region;


    public OciStorageService(
            @Value("${oci.config.tenancy-id}") String tenancyId,
            @Value("${oci.config.user-id}") String userId,
            @Value("${oci.config.fingerprint}") String fingerprint,
            @Value("${oci.config.private-key-path}") String privateKeyPath,
            @Value("${oci.config.region}") String region,
            @Value("${oci.bucket.namespace}") String namespace,
            @Value("${oci.bucket.name}") String bucketName
    ) {

        Supplier<InputStream> privateKeySupplier = () -> {
            try {
                // 방법 1: 절대 경로로 파일 읽기 (권장)
                InputStream inputStream = Files.newInputStream(Paths.get(privateKeyPath));
                log.info("OCI Private Key 파일 로드 성공: {}", privateKeyPath);
                return inputStream;
            } catch (IOException e) {
                log.error("OCI Private Key 파일 읽기 실패. Path: {}, Cause: {}", privateKeyPath, e.getMessage(), e);
                throw new OciUploadException("OCI Private Key 파일을 읽는데 실패했습니다. (서버 시작 불가)");
            }
        };

        AuthenticationDetailsProvider provider = SimpleAuthenticationDetailsProvider.builder()
                .tenantId(tenancyId)
                .userId(userId)
                .fingerprint(fingerprint)
                .privateKeySupplier(privateKeySupplier)
                .build();

        this.client = ObjectStorageClient.builder()
                .region(Region.fromRegionId(region))
                .build(provider);

        this.namespace = namespace;
        this.bucketName = bucketName;
        this.region = region;

        log.info("OciStorageService 초기화 완료 - Region: {}, Bucket: {}", region, bucketName);
    }

    public PreSignedUrlResponse createUploadPreSignedUrl(String uniqueFileName, String contentType) {

        // uniqueFileName & contentType 검증 로직
        String extension;
        int lastDotIndex = uniqueFileName.lastIndexOf('.');
        if (lastDotIndex > 0 ){
            extension = uniqueFileName.substring(lastDotIndex + 1).toLowerCase();
        } else {
            log.error("파일 확장자 검증 실패: 파일명 = {}", uniqueFileName);
            throw new OciUploadException("파일 확장자 추출에 실패했습니다.");
        }
        if(!AllowedFileType.isMatched(extension, contentType)){
            log.error("파일 확장자와 MIME 타입 불일치: 확장자 = {}, MIME 타입 = {}", extension, contentType);
            throw new NotAllowedFileType("파일 확장자와 MIME 타입이 일치하지 않습니다.");
        }



        // 한시간 만료 설정
        Date expirationDate = new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1));

        CreatePreauthenticatedRequestDetails details =
                CreatePreauthenticatedRequestDetails.builder()
                        .name("Upload-PAR-" + UUID.randomUUID().toString()) // PAR(Pre Authenticated Request)의 이름 (관리 용도)
                        .accessType(CreatePreauthenticatedRequestDetails.AccessType.ObjectWrite) // 접근 유형: 객체 쓰기 (PUT 요청 허용)
                        .objectName(uniqueFileName) // PAR이 적용될 객체 이름
                        .timeExpires(expirationDate) // 만료 시간
                        .build();

        CreatePreauthenticatedRequestRequest request =
                CreatePreauthenticatedRequestRequest.builder()
                        .namespaceName(namespace)
                        .bucketName(bucketName)
                        .createPreauthenticatedRequestDetails(details)
                        .build();

        try {

            CreatePreauthenticatedRequestResponse response = client.createPreauthenticatedRequest(request);

            String accessUrl = response.getPreauthenticatedRequest().getAccessUri();

            String parUrl = String.format(
                    "https://objectstorage.%s.oraclecloud.com%s",
                    region,
                    accessUrl
            );

            return new PreSignedUrlResponse(uniqueFileName, parUrl);
        } catch (Exception e) {
            log.error("OCI Pre-Signed URL creation failed. Cause: {}", e.getMessage(), e);
            throw new OciUploadException("파일 업로드 URL 생성 중 서버 오류가 발생했습니다.");
        }
    }

    // 파일을 다운로드 하거나 사진을 조회할 때 쓰여야할듯
    public String createFinalOciUrl(String uniqueFileName) {

        if (uniqueFileName == null || uniqueFileName.isBlank()) {
            return null;
        }

        try {
            String encodedFileName = URLEncoder.encode(uniqueFileName, StandardCharsets.UTF_8);

            encodedFileName = encodedFileName.replace("+", "%20");

            return String.format(
                    "https://objectstorage.%s.oraclecloud.com/n/%s/b/%s/o/%s",
                    region,
                    namespace,
                    bucketName,
                    encodedFileName
            );
        } catch (Exception e) {
            log.error("OCI Final-Oci URL creation failed. Cause: {}", e.getMessage(), e);
            throw new OciUploadException("OCI 최종 URL 생성 중 오류가 발생했습니다.");
        }
    }

    public void deleteUrls(List<String> fileKeys) {
        int success = 0;
        int fail = 0;
        for(String fileKey : fileKeys){
            try{
                deleteObject(fileKey);
                success++;

            } catch(Exception e){
                log.error("OCI URL 삭제 중 오류 발생(건너 뜀): url = {}, cause = {}", fileKey, e.getMessage());
                fail++;
            }
        }
        log.info("OCI URL 삭제 완료: 성공 {}건, 실패 {}건", success, fail);
    }


    @Retryable(
            value = {OciDeletionException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000)
    )
    private void deleteObject(String objectName) throws OciDeletionException {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucketName)
                .objectName(objectName)
                .build();

        try {
            client.deleteObject(request);
            log.info("OCI object deleted successfully: {}", objectName);
        } catch (Exception e) {
            log.error("OCI object deletion failed for {}. Cause: {}", objectName, e.getMessage(), e);
            throw new OciDeletionException("OCI 파일 삭제 요청 실패 " + objectName);
        }
    }

    public List<String> getOldFileKeys(int hours) {
        List<String> oldFiles = new ArrayList<>();


        //현재 기준시간 - hours 이전 시간
        Date timeThreshold = Date.from(Instant.now().minus(hours, ChronoUnit.HOURS));

        String nextToken = null; // 페이지네이션용 토큰

        try{
            //파일 수가 많을 걸 대비하여 do-while로 모든 페이지 조회
            do{
                ListObjectsRequest request = ListObjectsRequest.builder()
                        .namespaceName(namespace)
                        .bucketName(bucketName)
                        .fields("name,timeCreated") //필요한 필드만 조회 (최적화)
                        .start(nextToken)
                        .build();

                ListObjectsResponse response = client.listObjects(request);

                for(var obj : response.getListObjects().getObjects()) {
                    if(obj.getTimeCreated().before(timeThreshold)) { //임계값 이전 파일이면
                        oldFiles.add(obj.getName()); //파일명 추가
                    }
                }
                //다음 페이지 토큰 갱신
                nextToken = response.getListObjects().getNextStartWith();

            } while(nextToken != null);
        } catch(Exception e){
            log.error("OCI 오래된 파일 조회 실패. Cause: {}", e.getMessage(), e);
            throw new OciSearchException("OCI 오래된 파일 조회 중 서버 오류가 발생했습니다.");
        }

        return oldFiles;

    }

}