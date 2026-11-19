package com.marking.aimarkingsystem.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

/**
 * SimHash 指纹工具类。
 *
 * 原理简述：
 * 1. 文本归一化（去空白/标点、全角转半角、转小写），避免格式差异干扰；
 * 2. 切分为字符 n-gram 作为特征，词频作为权重；
 * 3. 每个特征用哈希函数映射到 64 位，按权重对 64 个 bit 位投票（+w / -w）；
 * 4. 逐位汇总，权重 > 0 记 1，否则记 0，得到 64 位指纹。
 *
 * 相似度：1 - 汉明距离 / 64。汉明距离指两个指纹异或后 1 的个数。
 * 相似内容只有少量 bit 翻转，因此距离小、相似度高。
 *
 * 纯静态工具类，不依赖 Spring 上下文，方便单测与异步消费者直接调用。
 */
public final class SimHashUtil {

    /** 指纹位数，固定 64 位，对应 16 位十六进制字符串 */
    public static final int BITS = 64;

    /** 字符 n-gram 的窗口大小：中文按 3 字滑窗，能兼顾语义与抗改写能力 */
    private static final int SHINGLE_SIZE = 3;

    private SimHashUtil() {
    }

    /**
     * 计算文本的 SimHash 指纹。
     *
     * @param text 原始文本（可为 null）
     * @return 16 位小写十六进制指纹；文本为空或归一化后无有效字符时返回 null
     */
    public static String simHash(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return null;
        }
        Map<String, Integer> features = extractFeatures(normalized);
        if (features.isEmpty()) {
            return null;
        }

        int[] weights = new int[BITS];
        MessageDigest digest = newMd5();
        for (Map.Entry<String, Integer> entry : features.entrySet()) {
            long hash = hash64(digest, entry.getKey());
            int weight = entry.getValue();
            for (int bit = 0; bit < BITS; bit++) {
                boolean one = ((hash >>> bit) & 1L) == 1L;
                weights[bit] += one ? weight : -weight;
            }
        }

        long fingerprint = 0L;
        for (int bit = 0; bit < BITS; bit++) {
            if (weights[bit] > 0) {
                fingerprint |= (1L << bit);
            }
        }
        return toHex(fingerprint);
    }

    /**
     * 计算两个指纹的汉明距离（不同 bit 的个数，0 ~ 64）。
     *
     * @throws IllegalArgumentException 指纹为空或不是合法十六进制
     */
    public static int hammingDistance(String hexA, String hexB) {
        long a = parseHex(hexA);
        long b = parseHex(hexB);
        return Long.bitCount(a ^ b);
    }

    /**
     * 计算两个指纹的相似度，范围 0.0 ~ 1.0，保留 4 位小数。
     */
    public static double similarity(String hexA, String hexB) {
        int distance = hammingDistance(hexA, hexB);
        double value = 1.0d - (double) distance / BITS;
        return Math.round(value * 10000d) / 10000d;
    }

    /**
     * 文本归一化：全角转半角（NFKC）、仅保留字母/数字、统一小写。
     *
     * 去除标点和空白后，同一份作答即使排版不同也能得到相同特征。
     */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String halfWidth = Normalizer.normalize(text, Normalizer.Form.NFKC);
        StringBuilder sb = new StringBuilder(halfWidth.length());
        for (int i = 0; i < halfWidth.length(); i++) {
            char c = halfWidth.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /** 按固定窗口切分 n-gram，统计词频作为权重 */
    private static Map<String, Integer> extractFeatures(String normalized) {
        Map<String, Integer> features = new HashMap<>();
        int length = normalized.length();
        if (length <= SHINGLE_SIZE) {
            features.put(normalized, 1);
            return features;
        }
        for (int i = 0; i <= length - SHINGLE_SIZE; i++) {
            String shingle = normalized.substring(i, i + SHINGLE_SIZE);
            features.merge(shingle, 1, Integer::sum);
        }
        return features;
    }

    /** 用 MD5 的前 8 字节作为 64 位哈希，避免额外引入哈希库 */
    private static long hash64(MessageDigest digest, String feature) {
        byte[] bytes = digest.digest(feature.getBytes(StandardCharsets.UTF_8));
        long hash = 0L;
        for (int i = 0; i < 8; i++) {
            hash = (hash << 8) | (bytes[i] & 0xFFL);
        }
        return hash;
    }

    private static MessageDigest newMd5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            // JDK 必然内置 MD5，理论上不可达
            throw new IllegalStateException("当前 JDK 不支持 MD5", e);
        }
    }

    private static String toHex(long fingerprint) {
        String hex = Long.toHexString(fingerprint);
        return "0".repeat(Math.max(0, 16 - hex.length())) + hex;
    }

    private static long parseHex(String hex) {
        if (hex == null || hex.isEmpty()) {
            throw new IllegalArgumentException("SimHash 指纹为空");
        }
        try {
            return Long.parseUnsignedLong(hex, 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("非法的 SimHash 指纹: " + hex, e);
        }
    }
}
