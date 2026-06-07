package com.scripttool.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "tencent.cos.bucket")
public class CosConfig {

    @Value("${tencent.cloud.secret-id}")
    private String secretId;

    @Value("${tencent.cloud.secret-key}")
    private String secretKey;

    @Value("${tencent.cos.region:ap-guangzhou}")
    private String region;

    @Value("${tencent.cos.bucket}")
    private String bucket;

    public String getBucket() { return bucket; }
    public String getRegion() { return region; }

    @Bean
    @ConditionalOnProperty(name = "tencent.cloud.secret-id")
    public COSClient cosClient() {
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        return new COSClient(cred, clientConfig);
    }
}
