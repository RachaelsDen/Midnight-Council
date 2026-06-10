package dev.kgoodwin.midnightcouncil.voice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
		byte[] encrypted = CryptoUtils.encrypt(original, key, seq, CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
		byte[] decrypted = CryptoUtils.decrypt(encrypted, key, seq, CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
		assertArrayEquals(original, decrypted);
	}

	@Test
	void differentSequenceNumbersProduceDifferentCiphertext() {
		byte[] data = "test data".getBytes();
		byte[] enc1 = CryptoUtils.encrypt(data, key, 1L, CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
		byte[] enc2 = CryptoUtils.encrypt(data, key, 2L, CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
		assertTrue(enc1.length > 0);
		assertTrue(enc2.length > 0);
		assertFalse(java.util.Arrays.equals(enc1, enc2));
	}

	@Test
	void sameSequenceDifferentDirectionsProduceDifferentCiphertext() {
		byte[] data = "test data".getBytes();
		byte[] inbound = CryptoUtils.encrypt(data, key, 7L, CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
		byte[] outbound = CryptoUtils.encrypt(data, key, 7L, CryptoUtils.DIRECTION_SERVER_TO_CLIENT);

		assertFalse(java.util.Arrays.equals(inbound, outbound));
	}

	@Test
	void decryptWithWrongDirectionFails() {
		byte[] data = "directional".getBytes();
		byte[] encrypted = CryptoUtils.encrypt(data, key, 5L, CryptoUtils.DIRECTION_CLIENT_TO_SERVER);

		assertThrows(Exception.class,
				() -> CryptoUtils.decrypt(encrypted, key, 5L, CryptoUtils.DIRECTION_SERVER_TO_CLIENT));
	}

	@Test
	void wrongKeyFailsToDecrypt() {
		byte[] data = "secret message".getBytes();
		byte[] encrypted = CryptoUtils.encrypt(data, key, 1L, CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
		SecretKey wrongKey;
		try {
			KeyGenerator kg = KeyGenerator.getInstance("AES");
			kg.init(256);
			wrongKey = kg.generateKey();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		assertThrows(Exception.class,
				() -> CryptoUtils.decrypt(encrypted, wrongKey, 1L, CryptoUtils.DIRECTION_CLIENT_TO_SERVER));
	}

	@Test
	void wrongSequenceNumberFailsToDecrypt() {
		byte[] data = "test data".getBytes();
		byte[] encrypted = CryptoUtils.encrypt(data, key, 100L, CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
		assertThrows(Exception.class,
				() -> CryptoUtils.decrypt(encrypted, key, 999L, CryptoUtils.DIRECTION_CLIENT_TO_SERVER));
	}

	@Test
	void emptyDataRoundTrip() {
		byte[] original = new byte[0];
		byte[] encrypted = CryptoUtils.encrypt(original, key, 1L, CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
		byte[] decrypted = CryptoUtils.decrypt(encrypted, key, 1L, CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
		assertArrayEquals(original, decrypted);
	}

	@Test
	void largeDataRoundTrip() {
		byte[] original = new byte[1024];
		for (int i = 0; i < original.length; i++) {
			original[i] = (byte) (i & 0xFF);
		}
		byte[] encrypted = CryptoUtils.encrypt(original, key, 7L, CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
		byte[] decrypted = CryptoUtils.decrypt(encrypted, key, 7L, CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
		assertArrayEquals(original, decrypted);
	}

	@Test
	void encryptedDataDiffersFromOriginal() {
		byte[] original = "plaintext".getBytes();
		byte[] encrypted = CryptoUtils.encrypt(original, key, 1L, CryptoUtils.DIRECTION_CLIENT_TO_SERVER);
		boolean different = false;
		for (int i = 0; i < Math.min(original.length, encrypted.length); i++) {
			if (original[i] != encrypted[i]) {
				different = true;
				break;
			}
		}
		assertTrue(different || original.length != encrypted.length);
	}

	@Test
	void wrapAndUnwrapKeyRoundTrip() throws Exception {
		KeyGenerator kg = KeyGenerator.getInstance("AES");
		kg.init(256);
		SecretKey sessionKey = kg.generateKey();

		byte[] wrapped = CryptoUtils.wrapKey(sessionKey, key);
		SecretKey unwrapped = CryptoUtils.unwrapKey(wrapped, key);

		assertArrayEquals(sessionKey.getEncoded(), unwrapped.getEncoded());
	}

	@Test
	void wrappedKeyIsLargerThanRawKey() throws Exception {
		KeyGenerator kg = KeyGenerator.getInstance("AES");
		kg.init(256);
		SecretKey sessionKey = kg.generateKey();

		byte[] wrapped = CryptoUtils.wrapKey(sessionKey, key);
		assertTrue(wrapped.length > sessionKey.getEncoded().length,
			"Wrapped key should include nonce and GCM tag");
	}

	@Test
	void unwrapWithWrongKeyFails() throws Exception {
		KeyGenerator kg = KeyGenerator.getInstance("AES");
		kg.init(256);
		SecretKey sessionKey = kg.generateKey();
		SecretKey wrongWrappingKey = kg.generateKey();

		byte[] wrapped = CryptoUtils.wrapKey(sessionKey, key);
		assertThrows(Exception.class, () -> CryptoUtils.unwrapKey(wrapped, wrongWrappingKey));
	}

	@Test
	void isValidFrameLengthAcceptsPositiveSmallValues() {
		assertTrue(CryptoUtils.isValidFrameLength(1));
		assertTrue(CryptoUtils.isValidFrameLength(256));
	}

	@Test
	void isValidFrameLengthRejectsZeroAndNegative() {
		assertFalse(CryptoUtils.isValidFrameLength(0));
		assertFalse(CryptoUtils.isValidFrameLength(-1));
		assertFalse(CryptoUtils.isValidFrameLength(257));
	}
}
