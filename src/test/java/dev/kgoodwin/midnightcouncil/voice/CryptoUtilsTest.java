package dev.kgoodwin.midnightcouncil.voice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.crypto.KeyGenerator;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;

import java.security.KeyPair;

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
	void generateEcdhKeyPairProducesValidKeyPair() throws Exception {
		KeyPair first = CryptoUtils.generateEcdhKeyPair();
		KeyPair second = CryptoUtils.generateEcdhKeyPair();

		assertNotNull(first.getPrivate());
		assertNotNull(first.getPublic());
		assertEquals(CryptoUtils.X25519_PUBLIC_KEY_LENGTH, CryptoUtils.encodeEcdhPublicKey(first.getPublic()).length);

		KeyAgreement firstAgreement = KeyAgreement.getInstance("X25519");
		firstAgreement.init(first.getPrivate());
		firstAgreement.doPhase(CryptoUtils.decodeEcdhPublicKey(CryptoUtils.encodeEcdhPublicKey(second.getPublic())), true);

		KeyAgreement secondAgreement = KeyAgreement.getInstance("X25519");
		secondAgreement.init(second.getPrivate());
		secondAgreement.doPhase(CryptoUtils.decodeEcdhPublicKey(CryptoUtils.encodeEcdhPublicKey(first.getPublic())), true);

		assertArrayEquals(firstAgreement.generateSecret(), secondAgreement.generateSecret());
	}

	@Test
	void deriveSessionKeyProducesConsistentKey() {
		byte[] sharedSecret = new byte[32];
		for (int i = 0; i < sharedSecret.length; i++) {
			sharedSecret[i] = (byte) (i + 1);
		}

		SecretKey firstDerived = CryptoUtils.deriveSessionKey(sharedSecret);
		SecretKey secondDerived = CryptoUtils.deriveSessionKey(sharedSecret);

		assertEquals("AES", firstDerived.getAlgorithm());
		assertArrayEquals(firstDerived.getEncoded(), secondDerived.getEncoded());
	}

	@Test
	void deriveSessionKeyDiffersForDifferentSharedSecrets() {
		byte[] firstSecret = new byte[32];
		byte[] secondSecret = new byte[32];
		secondSecret[0] = 1;

		SecretKey firstDerived = CryptoUtils.deriveSessionKey(firstSecret);
		SecretKey secondDerived = CryptoUtils.deriveSessionKey(secondSecret);

		assertFalse(java.util.Arrays.equals(firstDerived.getEncoded(), secondDerived.getEncoded()));
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
