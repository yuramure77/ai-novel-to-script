package com.scripttool.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.scripttool.config.CosConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "tencent.cos.bucket")
public class CosService {

    private final COSClient cosClient;
    private final CosConfig cosConfig;

    public CosService(COSClient cosClient, CosConfig cosConfig) {
        this.cosClient = cosClient;
        this.cosConfig = cosConfig;
    }

    /**
     * Upload image bytes to COS and return the permanent public URL.
     */
    public String uploadImage(byte[] imageBytes, String prefix) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String key = String.format("images/%s/%s/%s.png", prefix, date, UUID.randomUUID().toString().substring(0, 8));

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(imageBytes.length);
        metadata.setContentType("image/png");
        metadata.setCacheControl("public, max-age=31536000, immutable");

        ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);
        PutObjectRequest putRequest = new PutObjectRequest(cosConfig.getBucket(), key, inputStream, metadata);

        cosClient.putObject(putRequest);

        return String.format("https://%s.cos.%s.myqcloud.com/%s",
                cosConfig.getBucket(), cosConfig.getRegion(), key);
    }

    /**
     * Download image bytes from a URL.
     */
    public byte[] downloadImage(String imageUrl) {
        try (InputStream in = new URL(imageUrl).openStream()) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to download image from: " + imageUrl, e);
        }
    }
}
