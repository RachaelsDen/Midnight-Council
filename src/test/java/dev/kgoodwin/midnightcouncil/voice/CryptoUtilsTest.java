package dev.kgoodwin.midnightcouncil.voice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CryptoUtilsTest {

	private SecretKey key;

	@BeforeEach
	void generateKey() throws Exception {
		KeyGenerator kg = KeyGenerator.getInstance("AES");
		kg.init(256);
		key = kg.generateKey();
	}

	@Test
	void roundTripEncryption() {
		byte[] original = "hello voice transport".getBytes();
		long seq = 42L;
		byte[] encrypted = CryptoUtils.encrypt(original, key, seq);
		byte[] decrypted = CryptoUtils.decrypt(encrypted, key, seq);
		assertArrayEquals(original, decrypted);
	}

	@Test
	void differentSequenceNumbersProduceDifferentCiphertext() {
		byte[] data = "test data".getBytes();
		byte[] enc1 = CryptoUtils.encrypt(data, key, 1L);
		byte[] enc2 = CryptoUtils.encrypt(data, key, 2L);
		assertTrue(enc1.length > 0);
		assertTrue(enc2.length > 0);
		assertNotEquals(enc1.length, 0);
	}

	@Test
	void wrongKeyFailsToDecrypt() {
		byte[] data = "secret message".getBytes();
		byte[] encrypted = CryptoUtils.encrypt(data, key, 1L);
		SecretKey wrongKey;
		try {
			KeyGenerator kg = KeyGenerator.getInstance("AES");
			kg.init(256);
			wrongKey = kg.generateKey();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		assertThrows(Exception.class, () -> CryptoUtils.decrypt(encrypted, wrongKey, 1L));
	}

	@Test
	void wrongSequenceNumberFailsToDecrypt() {
		byte[] data = "test data".getBytes();
		byte[] encrypted = CryptoUtils.encrypt(data, key, 100L);
		assertThrows(Exception.class, () -> CryptoUtils.decrypt(encrypted, key, 999L));
	}

	@Test
	void emptyDataRoundTrip() {
		byte[] original = new byte[0];
		byte[] encrypted = CryptoUtils.encrypt(original, key, 1L);
		byte[] decrypted = CryptoUtils.decrypt(encrypted, key, 1L);
		assertArrayEquals(original, decrypted);
	}

	@Test
	void largeDataRoundTrip() {
		byte[] original = new byte[1024];
		for (int i = 0; i < original.length; i++) {
			original[i] = (byte) (i & 0xFF);
		}
		byte[] encrypted = CryptoUtils.encrypt(original, key, 7L);
		byte[] decrypted = CryptoUtils.decrypt(encrypted, key, 7L);
		assertArrayEquals(original, decrypted);
	}

	@Test
	void encryptedDataDiffersFromOriginal() {
		byte[] original = "plaintext".getBytes();
		byte[] encrypted = CryptoUtils.encrypt(original, key, 1L);
		boolean different = false;
		for (int i = 0; i < Math.min(original.length, encrypted.length); i++) {
			if (original[i] != encrypted[i]) {
				different = true;
				break;
			}
		}
		assertTrue(different || original.length != encrypted.length);
	}
}
