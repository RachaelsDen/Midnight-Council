package dev.kgoodwin.midnightcouncil.voice;

import java.nio.ByteBuffer;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class CryptoUtils {

	private static final String AES_GCM = "AES/GCM/NoPadding";
	private static final int GCM_TAG_LENGTH = 128;
	private static final int IV_LENGTH = 12;

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

	private static byte[] deriveIv(long sequenceNumber) {
		return ByteBuffer.allocate(IV_LENGTH).putLong(sequenceNumber).array();
	}
}
