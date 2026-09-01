package com.grash.service;

import com.grash.exception.CustomException;
import com.grash.model.File;
import com.grash.model.enums.FileType;
import com.grash.utils.Helper;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService implements StorageService {
    @Value("${storage.minio.endpoint}")
    private String minioEndpoint;
    @Value("${storage.minio.bucket}")
    private String minioBucket;
    @Value("${storage.minio.access-key}")
    private String minioAccessKey;
    @Value("${storage.minio.secret-key}")
    private String minioSecretKey;
    @Value("${storage.minio.public-endpoint}")
    private String minioPublicEndpoint;
    private final CacheService cacheService;

    private MinioClient minioClient;
    private static boolean configured = false;

    @PostConstruct
    private void init() {
        if (minioEndpoint.isEmpty() || minioBucket.isEmpty() || minioAccessKey.isEmpty() || minioSecretKey.isEmpty() || minioPublicEndpoint.isEmpty()) {
            return;
        }
        try {
            URI minioEndpointURI = new URI(minioEndpoint);
            MinioClient.Builder minioClientBuilder = MinioClient.builder()
                    .endpoint(minioEndpoint)
                    .credentials(minioAccessKey, minioSecretKey);

            if (Helper.isLocalhost(minioPublicEndpoint)) minioClientBuilder.httpClient(
                    new OkHttpClient.Builder().proxy(new Proxy(Proxy.Type.HTTP,
                            new InetSocketAddress(minioEndpointURI.getHost(), minioEndpointURI.getPort()))).build()
            );
            minioClient = minioClientBuilder.build();
            // Check if the bucket exists, create if it doesn't
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioBucket).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioBucket).build());
            }
            configured = true;
        } catch (MinioException | IOException | InvalidKeyException | NoSuchAlgorithmException e) {
            throw new CustomException("Error configuring MinIO: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public String upload(MultipartFile file, String folder) {
        checkIfConfigured();

        if (file == null || file.isEmpty()) {
            throw new CustomException("Uploaded file is empty.", HttpStatus.BAD_REQUEST);
        }

        String filePath = Helper.generateUniqueFilePath(file.getOriginalFilename(), folder);
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(filePath)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            return filePath;
        } catch (MinioException | IOException | InvalidKeyException | NoSuchAlgorithmException e) {
            log.error("MinIO error during upload to {}", filePath, e);
            throw new CustomException("Failed to save the file to storage.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public String upload(byte[] data, String fileName, String folder) {
        checkIfConfigured();

        if (data == null || data.length == 0) {
            throw new CustomException("Uploaded file is empty.", HttpStatus.BAD_REQUEST);
        }

        String filePath = Helper.generateUniqueFilePath(fileName, folder);
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(filePath)
                            .stream(inputStream, data.length, -1)
                            .contentType("image/jpeg")
                            .build()
            );
            return filePath;
        } catch (MinioException | IOException | InvalidKeyException | NoSuchAlgorithmException e) {
            log.error("MinIO error during upload to {}", filePath, e);
            throw new CustomException("Failed to save the file to storage.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void uploadAt(byte[] data, String filePath, String contentType) {
        checkIfConfigured();

        if (data == null || data.length == 0) {
            throw new CustomException("Uploaded file is empty.", HttpStatus.BAD_REQUEST);
        }

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(filePath)
                            .stream(inputStream, data.length, -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (MinioException | IOException | InvalidKeyException | NoSuchAlgorithmException e) {
            log.error("MinIO error during upload to {}", filePath, e);
            throw new CustomException("Failed to save the file to storage.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public boolean exists(String filePath) {
        checkIfConfigured();
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(filePath)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String generateSignedUrl(File file, long expirationMinutes) {
        // Merge: keep upstream's signed-URL caching, but generate through our overload that adds
        // response-header overrides (content-disposition/type) for the self-hosted download flow.
        return cacheService.getCachedOrGenerateSignedUrl(file, expirationMinutes,
                () -> generateSignedUrl(file.getPath(), expirationMinutes, responseHeaderOverrides(file)));
    }

    public String generateSignedUrl(String filePath, long expirationMinutes) {
        return generateSignedUrl(filePath, expirationMinutes, Collections.emptyMap());
    }

    private String generateSignedUrl(String filePath, long expirationMinutes, Map<String, String> extraQueryParams) {
        try {
            GetPresignedObjectUrlArgs.Builder builder = GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(minioBucket)
                    .object(filePath)
                    .expiry(Math.toIntExact(expirationMinutes), TimeUnit.MINUTES);
            if (extraQueryParams != null && !extraQueryParams.isEmpty()) {
                builder.extraQueryParams(extraQueryParams);
            }
            String internalUrl = minioClient.getPresignedObjectUrl(builder.build());
            if (!minioPublicEndpoint.isEmpty()) {
                return internalUrl.replace(minioEndpoint, minioPublicEndpoint);
            }
            return internalUrl;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    /**
     * MOD-004B — response-header overrides applied to the presigned URL. Non-image attachments are
     * served with {@code Content-Disposition: attachment} so active content (HTML/SVG) is downloaded
     * instead of being rendered inline from the same origin (stored-XSS mitigation). Images stay
     * inline so previews keep working.
     */
    static Map<String, String> responseHeaderOverrides(File file) {
        Map<String, String> params = new HashMap<>();
        if (file != null && file.getType() != FileType.IMAGE) {
            params.put("response-content-disposition", "attachment");
        }
        return params;
    }

    @Override
    public void delete(String filePath) {
        checkIfConfigured();
        try {
            // MinIO/S3 removeObject is idempotent: deleting an absent key succeeds.
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(filePath)
                            .build()
            );
        } catch (MinioException | IOException | InvalidKeyException | NoSuchAlgorithmException e) {
            log.warn("MinIO error deleting object {}", filePath, e);
            throw new CustomException("Failed to delete the file from storage.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public byte[] download(String filePath) {
        checkIfConfigured();
        InputStream inputStream = null;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(filePath)
                            .build()
            );
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            return byteArrayOutputStream.toByteArray();
        } catch (MinioException | IOException | InvalidKeyException | NoSuchAlgorithmException e) {
            throw new CustomException("Error retrieving file", HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                byteArrayOutputStream.close();
            } catch (IOException e) {
                throw new CustomException("Error closing stream", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    public byte[] download(File file) {
        checkIfConfigured();
        return download(file.getPath());
    }

    private void checkIfConfigured() {
        if (!configured)
            throw new CustomException("MinIO is not configured. Please define the MinIO credentials in the env " +
                    "variables",
                    HttpStatus.INTERNAL_SERVER_ERROR);
    }
}