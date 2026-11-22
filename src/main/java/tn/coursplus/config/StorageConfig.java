package tn.coursplus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import tn.coursplus.service.storage.LocalObjectStore;
import tn.coursplus.service.storage.ObjectStore;
import tn.coursplus.service.storage.S3ObjectStore;

import java.io.IOException;

/**
 * Choisit où ranger les vidéos et PDF :
 * app.storage.type = local (disque, par défaut) ou s3 (Cloudflare R2 et autres stockages compatibles S3).
 */
@Configuration
public class StorageConfig {

    @Bean
    ObjectStore objectStore(
            @Value("${app.storage.type:local}") String type,
            @Value("${app.storage.dir}") String dir,
            @Value("${app.storage.s3.endpoint:}") String endpoint,
            @Value("${app.storage.s3.region:auto}") String region,
            @Value("${app.storage.s3.bucket:}") String bucket,
            @Value("${app.storage.s3.access-key:}") String accessKey,
            @Value("${app.storage.s3.secret-key:}") String secretKey) throws IOException {
        return switch (type.trim().toLowerCase()) {
            case "local" -> new LocalObjectStore(dir);
            case "s3" -> {
                require("R2_ENDPOINT", endpoint);
                require("R2_BUCKET", bucket);
                require("R2_ACCESS_KEY_ID", accessKey);
                require("R2_SECRET_ACCESS_KEY", secretKey);
                yield new S3ObjectStore(endpoint, region, bucket, accessKey, secretKey);
            }
            default -> throw new IllegalStateException(
                    "STORAGE_TYPE inconnu : \"" + type + "\" (valeurs possibles : local, s3)");
        };
    }

    private static void require(String variable, String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("STORAGE_TYPE=s3 : la variable " + variable + " est obligatoire");
        }
    }
}
