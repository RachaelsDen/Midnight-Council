package dev.kgoodwin.midnightcouncil.voice;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class CryptoUtils {

	private static final String AES_GCM = "AES/GCM/NoPadding";
	private static final int GCM_TAG_LENGTH = 128;
	private static final int IV_LENGTH = 12;
	private static final int MAX_FRAME_LENGTH = 256;

	private CryptoUtils() {
	}

	static byte[] encrypt(byte[] data, SecretKey key, long sequenceNumber) {
		try {
			Cipher cipher = Cipher.getInstance(AES_GCM);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, deriveIv(sequenceNumber)));
			return cipher.doFinal(data);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to encrypt voice payload", e);
		}
	}

	static byte[] decrypt(byte[] encryptedData, SecretKey key, long sequenceNumber) {
		try {
			Cipher cipher = Cipher.getInstance(AES_GCM);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, deriveIv(sequenceNumber)));
			return cipher.doFinal(encryptedData);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to decrypt voice payload", e);
		}
	}

	static byte[] wrapKey(SecretKey key, SecretKey wrappingKey) {
		try {
			byte[] nonce = new byte[IV_LENGTH];
			new SecureRandom().nextBytes(nonce);
			Cipher cipher = Cipher.getInstance(AES_GCM);
			cipher.init(Cipher.ENCRYPT_MODE, wrappingKey, new GCMParameterSpec(GCM_TAG_LENGTH, nonce));
			byte[] encrypted = cipher.doFinal(key.getEncoded());
			ByteBuffer result = ByteBuffer.allocate(IV_LENGTH + encrypted.length);
			result.put(nonce);
			result.put(encrypted);
			return result.array();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to wrap key", e);
		}
	}

	static SecretKey unwrapKey(byte[] wrapped, SecretKey wrappingKey) {
		try {
			ByteBuffer buf = ByteBuffer.wrap(wrapped);
			byte[] nonce = new byte[IV_LENGTH];
			buf.get(nonce);
			byte[] encrypted = new byte[wrapped.length - IV_LENGTH];
			buf.get(encrypted);
			Cipher cipher = Cipher.getInstance(AES_GCM);
			cipher.init(Cipher.DECRYPT_MODE, wrappingKey, new GCMParameterSpec(GCM_TAG_LENGTH, nonce));
			byte[] keyBytes = cipher.doFinal(encrypted);
			return new SecretKeySpec(keyBytes, "AES");
		} catch (Exception e) {
			throw new IllegalStateException("Failed to unwrap key", e);
		}
	}

	static boolean isValidFrameLength(int length) {
		return length > 0 && length <= MAX_FRAME_LENGTH;
	}

	private static byte[] deriveIv(long sequenceNumber) {
		return ByteBuffer.allocate(IV_LENGTH).putLong(sequenceNumber).array();
	}
}
