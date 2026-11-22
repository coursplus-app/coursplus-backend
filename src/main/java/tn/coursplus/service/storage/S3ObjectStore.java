package tn.coursplus.service.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRange;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * Stockage dans un bucket compatible S3 (Cloudflare R2, Backblaze B2, MinIO, AWS S3...).
 * Le bucket reste privé : les fichiers ne sortent que par MediaController, après vérification des droits.
 */
public class S3ObjectStore implements ObjectStore {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStore.class);

    private final S3Client client;
    private final String bucket;

    public S3ObjectStore(String endpoint, String region, String bucket, String accessKey, String secretKey) {
        this.bucket = bucket;
        this.client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                // Les SDK récents ajoutent des sommes de contrôle que R2 et la plupart des S3 "maison" refusent.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    /** Pour les tests : client déjà construit. */
    S3ObjectStore(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, InputStream in, long length) throws IOException {
        PutObjectRequest request = PutObjectRequest.builder().bucket(bucket).key(key).build();
        try {
            if (length < 0) {
                client.putObject(request, RequestBody.fromBytes(in.readAllBytes())); // petits fichiers (données de démo)
            } else {
                client.putObject(request, RequestBody.fromInputStream(in, length));
            }
        } catch (SdkException e) {
            throw new IOException("Envoi vers le stockage impossible : " + e.getMessage(), e);
        }
    }

    @Override
    public ObjectChunk open(String key, String rangeHeader) {
        GetObjectRequest.Builder request = GetObjectRequest.builder().bucket(bucket).key(key);
        HttpRange range = null;
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            range = LocalObjectStore.firstRange(rangeHeader);
            request.range(HttpRange.toString(java.util.List.of(range)));
        }
        ResponseInputStream<GetObjectResponse> stream;
        try {
            stream = client.getObject(request.build());
        } catch (NoSuchKeyException e) {
            return null;
        } catch (S3Exception e) {
            if (e.statusCode() == 416) {
                throw LocalObjectStore.rangeNotSatisfiable();
            }
            if (e.statusCode() == 404) {
                return null;
            }
            throw e;
        }
        GetObjectResponse head = stream.response();
        if (range == null) {
            long size = head.contentLength();
            return new ObjectChunk(stream, 0, size - 1, size, false);
        }
        String contentRange = head.contentRange(); // "bytes 0-99/1000"
        if (contentRange != null) {
            return fromContentRange(stream, contentRange);
        }
        // Le stockage a ignoré la plage et renvoie tout le fichier : on découpe nous-mêmes.
        long size = head.contentLength();
        long start = range.getRangeStart(size);
        long end = range.getRangeEnd(size);
        try {
            stream.skipNBytes(start);
        } catch (IOException e) {
            abortQuietly(stream);
            throw new IllegalStateException("Lecture impossible : " + key, e);
        }
        return new ObjectChunk(new LocalObjectStore.LimitedInputStream(stream, end - start + 1), start, end, size, true);
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            log.warn("Suppression de {} impossible : {}", key, e.getMessage());
        }
    }

    private static ObjectChunk fromContentRange(InputStream stream, String contentRange) {
        // "bytes <début>-<fin>/<total>"
        String spec = contentRange.substring(contentRange.indexOf(' ') + 1);
        int slash = spec.indexOf('/');
        int dash = spec.indexOf('-');
        long start = Long.parseLong(spec.substring(0, dash));
        long end = Long.parseLong(spec.substring(dash + 1, slash));
        long total = Long.parseLong(spec.substring(slash + 1));
        return new ObjectChunk(stream, start, end, total, true);
    }

    private static void abortQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // rien à faire
        }
    }
}
