package com.chorus.observe.export;

import com.chorus.observe.model.ExportConfig;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/**
 * S3-compatible export client supporting MinIO and AWS S3 via custom endpoint.
 */
public class S3ExportClient {

    private static final Logger LOG = LoggerFactory.getLogger(S3ExportClient.class);

    private final CredentialEncryptionService encryptionService;

    public S3ExportClient(@NonNull CredentialEncryptionService encryptionService) {
        this.encryptionService = Objects.requireNonNull(encryptionService);
    }

    public void upload(@NonNull ExportConfig config, @NonNull Path localFile, @NonNull String s3Key) {
        String accessKey = encryptionService.decrypt(Objects.requireNonNull(config.accessKeyId()));
        String secretKey = encryptionService.decrypt(Objects.requireNonNull(config.secretAccessKey()));

        S3ClientBuilder builder = S3Client.builder()
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
            .region(Region.of(config.region() != null ? config.region() : "us-east-1"));

        if (config.endpointUrl() != null && !config.endpointUrl().isBlank()) {
            com.chorus.observe.notification.UrlValidator.validate(config.endpointUrl());
            builder.endpointOverride(URI.create(config.endpointUrl()));
            builder.forcePathStyle(true);
        }

        try (S3Client s3 = builder.build()) {
            String bucket = config.bucketName();
            String key = (config.pathPrefix() != null ? config.pathPrefix() : "") + s3Key;

            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/octet-stream")
                .build();

            s3.putObject(request, RequestBody.fromFile(localFile));
            LOG.info("Uploaded {} to s3://{}/{}", localFile, bucket, key);
        }
    }
}
