/*
 * Copyright IBM Corp. 2016 All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.vicos.common.util;

import com.ibm.vicos.common.ClientIdentifier;
import com.ibm.vicos.common.crypto.CryptoUtils;
import com.ibm.vicos.common.crypto.DSACryptoUtilsImpl;
import com.ibm.vicos.common.crypto.RSACryptoUtilsImpl;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.concurrent.atomic.AtomicLong;

public class ClientIdentityGenerator {

    public static final String RSA_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA28VHAL3vN/eTO31eb44hQ4YBbUCSDIBtLOZroMVRDErEfXlpSqZgbs4YvyRRYTAGtnyGpeCTiVCDofIiRGW8kpCLBgeUSWkA8DE260Wz9KtN1RwpK83cRwCeYQXc1ozuUBFTYxmxST+un2x6b8kG88ZdQXwhtpB0LLRUJNLNNyNeu+7PainkKVBWJUmIF9k8ZpEtzHvwW0nnFH8+ceYxsfavES5oN6SoJk5FINCqo3r2kd3f5ZHjem5XsGTQbEw6eqVAOkhnESbc3/g1Mj6tGyPbVOd5KrLWXXkzHQCERiiIQVUZh8E7bflKzvI+UR3VhMsAjb2riJk6YYdb0sLadQIDAQAB";
    public static final String RSA_PRIVATE_KEY = "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDbxUcAve8395M7fV5vjiFDhgFtQJIMgG0s5mugxVEMSsR9eWlKpmBuzhi/JFFhMAa2fIal4JOJUIOh8iJEZbySkIsGB5RJaQDwMTbrRbP0q03VHCkrzdxHAJ5hBdzWjO5QEVNjGbFJP66fbHpvyQbzxl1BfCG2kHQstFQk0s03I1677s9qKeQpUFYlSYgX2TxmkS3Me/BbSecUfz5x5jGx9q8RLmg3pKgmTkUg0KqjevaR3d/lkeN6blewZNBsTDp6pUA6SGcRJtzf+DUyPq0bI9tU53kqstZdeTMdAIRGKIhBVRmHwTtt+UrO8j5RHdWEywCNvauImTphh1vSwtp1AgMBAAECggEBAK0O2Q2dgz2IKBllbLLdshXw9Tip6xgLHW2I+OG930eVrMq9i4ssHq3CfzwtBiRI5FBZGNKQWL19eFpTSGah0m97gO47k6qHFCUQLBXyanXVW1Hgfg8vaTFELHNsYW1Vxuen7QkRl0h97C1AB9306BOYdaD2dbnEYKc/TS8+DDlInBXOu1GFgvEpxXH6ocDQdgmGkWm7hx5OPaugM+SDiaDNyggdZqMoMxDudfy7iVuZmQSDVPGLCGXK2AUwoz6r7Wc3065nMBt+vCEHasBvWOr/0Lz85blKio18WCC9pRpwG9WPgUA+LFlY4B//oO6zuJ4qYh+dCelpEBD0ciQfZYECgYEA9xhuaElr6RjbhXvwuGVbAe0+4URzgFSQogGoMqhQHBFFbEyISkWTbWambXXgpc0XQldVAvn52BlDwfXfT+GFzPZM6to4TxdlkrDwh7Tqm2Nuv9x2hessbm0zR6S+x0JtUbaz4n32ABlbkOSVfpXUTeOhqZFyJV/kDejQ6RMfWyECgYEA47DDF5LzOaN0SAJQdslqMD7LMDM48Vldb1kkTec+TTRG86M+BpGQszhoegBXT/WpPj73tum9IUQ112GsqeRP/7REmIzT4nzE9R7+pophEGvSb3vOaKENrS7Yoq2PKJ+uOZWAaqoEoewismVYjXq6JybrEDpe6Y06kONh1x9zCNUCgYEA2QAwvg904HH/H7xYDGiewxNqfIUT9mjN2CkiYNKl//A/CrwvmSJ/Nb3HfSvfjVyPB6kQtJt6Coktk2JtqoaBbh4EMOBSQUtn3arcoiGFQ8/QDFj+EeAV1ii6tzl8fhvdS7zrP4alizK50oacr12/5GN7ryJXn3EWJA0JoxczG2ECgYEAqN1mw2wUAXJtgh5dSMv2elOH5HaIy86IUlWdbD2NYjrcSEOQc9SvqYuzSJBKMR4z59tRYpIV178740IoiVOv1SgAG9eOKZJ5jIXxPNiyRjiBOHdszQJtHzz4JCZuXyWWVFTBidoQV3rG/OHW/A7CUtk1SrLPw40fm2dYH/DOIwUCgYAMFxQWctct/F6NcBKCljuof6bOMFs4SzsjIjW2t2KhjwKhfOESOzcy/jap9XlN+c9T4Gb/yqW5Pe7Km2iJ6/eLA30oDOS6XLkWaQ0D+k2SrixHaNqbJ5pp1Mk9o9Z67zNno9EIjDmhFAw5ENGn2UPOJ4MqDopKJwzvZoYQCkAOxA==";

    public static final String DSA_PUBLIC_KEY = "MIIDQjCCAjUGByqGSM44BAEwggIoAoIBAQCPeTXZuarpv6vtiHrPSVG28y7FnjuvNxjo6sSWHz79NgbnQ1GpxBgzObgJ58KuHFObp0dbhdARrbi0eYd1SYRpXKwOjxSzNggooi/6JxEKPWKpk0U0CaD+aWxGWPhL3SCBnDcJoBBXsZWtzQAjPbpUhLYpH51kjviDRIZ3l5zsBLQ0pqwudemYXeI9sCkvwRGMn/qdgYHnM423krcw17njSVkvaAmYchU5Feo9a4tGU8YzRY+AOzKkwuDycpAlbk4/ijsIOKHEUOThjBopo33fXqFD3ktm/wSQPtXPFiPhWNSHxgjpfyEc2B3KI8tuOAdl+CLjQr5ITAV2OTlgHNZnAh0AuvaWpoV499/e5/pnyXfHhe8ysjO65YDAvNVpXQKCAQAWplxYIEhQcE51AqOXVwQNNNo6NHjBVNTkpcAtJC7gT5bmHkvQkEq9rI837rHgnzGC0jyQQ8tkL4gAQWDt+coJsyB2p5wypifyRz6Rh5uixOdEvSCBVEy1W4AsNo0fqD7UielOD6BojjJCilx4xHjGjQUntxyaOrsLC+EsRGiWOefTznTbEBplqiuH9kxoJts+xy9LVZmDS7TtsC98kOmkltOlXVNb6/xF1PYZ9j897buHOSXC8iTgdzEpbaiH7B5HSPh++1/et1SEMWsiMt7lU92vAhErDR8C2jCXMiT+J67ai51LKSLZuovjntnhA6Y8UoELxoi34u1DFuHvF9veA4IBBQACggEAEkxSGDej5HMsau3CZJ7HZXa4ZlsSFSpHNP7ZKRzdoZqoLP7fnZY6YtJ1jMUOGQl5tcYnH+VtST5T8hvobqb2QN0az0YzkXwbaTudDLewn4lVufyfq28exCptPH/RFYEx0d4bqYCzYoGvePJ3hr4525UPUhFSuT5/ub6V0MilXtaQn1DwWmf9XVEklRppDuyU90c4OqQWKMyWpT+canjG6NZH93cUqV11azF1FGIAfdaunjN4gbGXpzl7VgZ+pTwQ34XCaNMs2hfHavNpPGSmIu8NYHDcJbmEAz+1pbka+H6JnVqKUUfZNlycQ22ERZDR5ZCJs/eBg74OIjfnb1Suqg==";
    public static final String DSA_PRIVATE_KEY = "MIICXAIBADCCAjUGByqGSM44BAEwggIoAoIBAQCPeTXZuarpv6vtiHrPSVG28y7FnjuvNxjo6sSWHz79NgbnQ1GpxBgzObgJ58KuHFObp0dbhdARrbi0eYd1SYRpXKwOjxSzNggooi/6JxEKPWKpk0U0CaD+aWxGWPhL3SCBnDcJoBBXsZWtzQAjPbpUhLYpH51kjviDRIZ3l5zsBLQ0pqwudemYXeI9sCkvwRGMn/qdgYHnM423krcw17njSVkvaAmYchU5Feo9a4tGU8YzRY+AOzKkwuDycpAlbk4/ijsIOKHEUOThjBopo33fXqFD3ktm/wSQPtXPFiPhWNSHxgjpfyEc2B3KI8tuOAdl+CLjQr5ITAV2OTlgHNZnAh0AuvaWpoV499/e5/pnyXfHhe8ysjO65YDAvNVpXQKCAQAWplxYIEhQcE51AqOXVwQNNNo6NHjBVNTkpcAtJC7gT5bmHkvQkEq9rI837rHgnzGC0jyQQ8tkL4gAQWDt+coJsyB2p5wypifyRz6Rh5uixOdEvSCBVEy1W4AsNo0fqD7UielOD6BojjJCilx4xHjGjQUntxyaOrsLC+EsRGiWOefTznTbEBplqiuH9kxoJts+xy9LVZmDS7TtsC98kOmkltOlXVNb6/xF1PYZ9j897buHOSXC8iTgdzEpbaiH7B5HSPh++1/et1SEMWsiMt7lU92vAhErDR8C2jCXMiT+J67ai51LKSLZuovjntnhA6Y8UoELxoi34u1DFuHvF9veBB4CHCqygwF57DMuAp8tJO+5iOeY66tovRvdNMP/MC8=";

    private final static AtomicLong clientInstanceCounter = new AtomicLong(0);

    public static ClientIdentifier generate(String prefix, Class<? extends CryptoUtils> clazz) {
        if (clazz.equals(RSACryptoUtilsImpl.class)) {
            return generateRSA(prefix);
        } else if (clazz.equals(DSACryptoUtilsImpl.class)) {
            return generateDSA(prefix);
        }
        return generate(prefix);
    }

    public static ClientIdentifier generateRSA(String prefix) {
        final CryptoUtils cryptoUtils = new RSACryptoUtilsImpl();
        final long instanceCount = clientInstanceCounter.incrementAndGet();
        return generate(prefix + instanceCount,
                cryptoUtils.convert2PublicKey(RSA_PUBLIC_KEY),
                cryptoUtils.convert2PrivateKey(RSA_PRIVATE_KEY));
    }

    public static ClientIdentifier generateDSA(String prefix) {
        final CryptoUtils cryptoUtils = new DSACryptoUtilsImpl();
        final long instanceCount = clientInstanceCounter.incrementAndGet();
        return generate(prefix + instanceCount,
                cryptoUtils.convert2PublicKey(DSA_PUBLIC_KEY),
                cryptoUtils.convert2PrivateKey(DSA_PRIVATE_KEY));
    }

    public static ClientIdentifier generate(String prefix) {
        final long instanceCount = clientInstanceCounter.incrementAndGet();
        return ClientIdentifier.builder().setClientId(prefix + instanceCount).build();
    }

    public static ClientIdentifier generate(String clientId, PublicKey publicKey, PrivateKey privateKey) {
        return ClientIdentifier.builder()
                .setClientId(clientId)
                .setPublicKey(publicKey)
                .setPrivateKey(privateKey)
                .build();
    }
}
