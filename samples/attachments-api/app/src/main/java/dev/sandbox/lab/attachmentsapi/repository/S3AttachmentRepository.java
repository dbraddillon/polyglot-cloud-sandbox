package dev.sandbox.lab.attachmentsapi.repository;

import dev.sandbox.lab.attachmentsapi.domain.AttachmentContent;
import dev.sandbox.lab.attachmentsapi.domain.AttachmentDetail;
import dev.sandbox.lab.attachmentsapi.domain.AttachmentSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class S3AttachmentRepository implements AttachmentRepository {
    private static final String FILENAME_METADATA_KEY = "filename";

    private final S3Client s3;
    private final String bucketName;

    public S3AttachmentRepository(S3Client s3, @Value("${attachments.bucket-name}") String bucketName) {
        this.s3 = s3;
        this.bucketName = bucketName;
    }

    @Override
    public List<AttachmentSummary> findAll() {
        // .stream().map(...).toList() (Java 16+, an immutable-list-returning shortcut for
        // .collect(Collectors.toList())) is the same shape as C#'s LINQ .Select(...).ToList() -
        // ListObjectsV2's S3Object results get projected into this API's own shape in one pass,
        // no intermediate mutable ArrayList built up by hand with a for loop.
        return s3.listObjectsV2(ListObjectsV2Request.builder().bucket(bucketName).build())
                .contents()
                .stream()
                .map(obj -> new AttachmentSummary(obj.key(), obj.size(), obj.lastModified()))
                .toList();
    }

    @Override
    public AttachmentDetail upload(String filename, String contentType, byte[] content) {
        String key = UUID.randomUUID().toString();
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(contentType)
                        .metadata(Map.of(FILENAME_METADATA_KEY, filename))
                        .build(),
                RequestBody.fromBytes(content));
        return new AttachmentDetail(key, filename, contentType, content.length);
    }

    @Override
    public Optional<AttachmentContent> download(String key) {
        try {
            ResponseBytes<GetObjectResponse> response = s3.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucketName).key(key).build());
            GetObjectResponse metadata = response.response();
            String filename = metadata.metadata().getOrDefault(FILENAME_METADATA_KEY, key);
            return Optional.of(new AttachmentContent(filename, metadata.contentType(), response.asByteArray()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void deleteByKey(String key) {
        // S3's DeleteObject is idempotent by design - it doesn't error, and doesn't tell you
        // whether anything was actually there, whether the key existed or not. Matches this
        // repo's other DELETE endpoints (task-api, node-api): always 204, no existence check
        // first, rather than a HeadObject round trip purely to decide which status code to
        // return.
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
    }
}
