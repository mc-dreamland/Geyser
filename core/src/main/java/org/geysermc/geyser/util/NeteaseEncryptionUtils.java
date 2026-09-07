/*
 * Copyright (c) 2019-2026 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.geysermc.geyser.util;

import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.codec.v428.Bedrock_v428;
import org.cloudburstmc.protocol.bedrock.netty.codec.FrameIdCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.encryption.BedrockEncryptionDecoder;
import org.cloudburstmc.protocol.bedrock.netty.codec.encryption.BedrockEncryptionEncoder;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.lang.JoseException;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * The small subset of Bedrock login cryptography needed after a NetEase token chain has been validated.
 * This class deliberately has no dependency on Cloudburst's EncryptionUtils, whose static initializer
 * retrieves Microsoft discovery and OpenID data.
 */
final class NeteaseEncryptionUtils {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private NeteaseEncryptionUtils() {
    }

    static ECPublicKey parseKey(String base64Key) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] encodedKey = Base64.getDecoder().decode(base64Key);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return (ECPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(encodedKey));
    }

    static KeyPair createKeyPair() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp384r1"));
        return generator.generateKeyPair();
    }

    static byte[] verifyClientData(String clientJwt, PublicKey identityPublicKey) throws JoseException {
        JsonWebSignature signature = new JsonWebSignature();
        signature.setCompactSerialization(clientJwt);
        signature.setKey(identityPublicKey);
        return signature.verifySignature() ? signature.getUnverifiedPayloadBytes() : null;
    }

    static byte[] generateRandomToken() {
        byte[] token = new byte[16];
        SECURE_RANDOM.nextBytes(token);
        return token;
    }

    static String createHandshakeJwt(KeyPair serverKeyPair, byte[] token) throws JoseException {
        JsonWebSignature signature = new JsonWebSignature();
        signature.setAlgorithmHeaderValue(AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384);
        signature.setHeader("x5u", Base64.getEncoder().encodeToString(serverKeyPair.getPublic().getEncoded()));
        signature.setKey(serverKeyPair.getPrivate());

        JwtClaims claims = new JwtClaims();
        claims.setClaim("salt", Base64.getEncoder().encodeToString(token));
        signature.setPayload(claims.toJson());
        return signature.getCompactSerialization();
    }

    static SecretKey getSecretKey(KeyPair serverKeyPair, PublicKey clientPublicKey, byte[] token)
            throws NoSuchAlgorithmException, InvalidKeyException {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(serverKeyPair.getPrivate());
        agreement.doPhase(clientPublicKey, true);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(token);
        digest.update(agreement.generateSecret());
        return new SecretKeySpec(digest.digest(), "AES");
    }

    static void enableEncryption(BedrockPeer peer, SecretKey secretKey)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException {
        Objects.requireNonNull(peer, "peer");
        Objects.requireNonNull(secretKey, "secretKey");
        if (!"AES".equals(secretKey.getAlgorithm())) {
            throw new IllegalArgumentException("Invalid key algorithm");
        }
        if (peer.getChannel().pipeline().get(BedrockEncryptionEncoder.class) != null
                || peer.getChannel().pipeline().get(BedrockEncryptionDecoder.class) != null) {
            throw new IllegalStateException("Encryption is already enabled");
        }

        boolean useCtr = peer.getCodec().getProtocolVersion() >= Bedrock_v428.CODEC.getProtocolVersion();
        Cipher encryptionCipher = createCipher(useCtr, true, secretKey);
        Cipher decryptionCipher = createCipher(useCtr, false, secretKey);

        peer.getChannel().pipeline().addAfter(FrameIdCodec.NAME, BedrockEncryptionEncoder.NAME,
                new BedrockEncryptionEncoder(secretKey, encryptionCipher));
        peer.getChannel().pipeline().addAfter(FrameIdCodec.NAME, BedrockEncryptionDecoder.NAME,
                new BedrockEncryptionDecoder(secretKey, decryptionCipher));
    }

    static Cipher createCipher(boolean useCtr, boolean encrypt, SecretKey key)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException {
        byte[] iv;
        String transformation;
        if (useCtr) {
            iv = new byte[16];
            System.arraycopy(key.getEncoded(), 0, iv, 0, 12);
            iv[15] = 2;
            transformation = "AES/CTR/NoPadding";
        } else {
            iv = Arrays.copyOf(key.getEncoded(), 16);
            transformation = "AES/CFB8/NoPadding";
        }

        Cipher cipher = Cipher.getInstance(transformation);
        cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        return cipher;
    }
}
