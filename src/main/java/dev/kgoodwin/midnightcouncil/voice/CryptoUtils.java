package dev.kgoodwin.midnightcouncil.voice;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class CryptoUtils {

	private static final String AES_GCM = "AES/GCM/NoPadding";
	private static final int GCM_TAG_LENGTH = 128;
	private static final int IV_LENGTH = 12;
	private static final int MAX_FRAME_LENGTH = 256;
	private static final byte[] SESSION_KEY_DOMAIN = "midnight-council-voice-session-v1"
			.getBytes(StandardCharsets.UTF_8);
	private static final byte[] X25519_X509_PREFIX = initX25519PublicKeyPrefix();
	static final byte DIRECTION_CLIENT_TO_SERVER = 0x01;
	static final byte DIRECTION_SERVER_TO_CLIENT = 0x02;
	static final int X25519_PUBLIC_KEY_LENGTH = 32;

	private CryptoUtils() {
	}

	static byte[] encrypt(byte[] data, SecretKey key, long sequenceNumber, byte direction) {
		try {
			Cipher cipher = Cipher.getInstance(AES_GCM);
			cipher.init(Cipher.ENCRYPT_MODE, deriveTrafficKey(key, direction),
					new GCMParameterSpec(GCM_TAG_LENGTH, deriveIv(sequenceNumber)));
			return cipher.doFinal(data);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to encrypt voice payload", e);
		}
	}

	static byte[] decrypt(byte[] encryptedData, SecretKey key, long sequenceNumber, byte direction) {
		try {
			Cipher cipher = Cipher.getInstance(AES_GCM);
			cipher.init(Cipher.DECRYPT_MODE, deriveTrafficKey(key, direction),
					new GCMParameterSpec(GCM_TAG_LENGTH, deriveIv(sequenceNumber)));
			return cipher.doFinal(encryptedData);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to decrypt voice payload", e);
		}
	}

	static KeyPair generateEcdhKeyPair() {
		try {
			KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("X25519");
			return keyPairGenerator.generateKeyPair();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to generate X25519 key pair", e);
		}
	}

	static SecretKey deriveSessionKey(byte[] ecdhSharedSecret) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(ecdhSharedSecret);
			digest.update(SESSION_KEY_DOMAIN);
			return new SecretKeySpec(digest.digest(), "AES");
		} catch (Exception e) {
			throw new IllegalStateException("Failed to derive session key", e);
		}
	}

	static byte[] encodeEcdhPublicKey(PublicKey publicKey) {
		byte[] encoded = publicKey.getEncoded();
		if (encoded.length != X25519_X509_PREFIX.length + X25519_PUBLIC_KEY_LENGTH
				|| !Arrays.equals(X25519_X509_PREFIX, Arrays.copyOf(encoded, X25519_X509_PREFIX.length))) {
			throw new IllegalArgumentException("Public key is not an X25519 public key");
		}
		return Arrays.copyOfRange(encoded, X25519_X509_PREFIX.length, encoded.length);
	}

	static PublicKey decodeEcdhPublicKey(byte[] rawPublicKey) {
		if (rawPublicKey.length != X25519_PUBLIC_KEY_LENGTH) {
			throw new IllegalArgumentException("X25519 public key must be 32 bytes");
		}
		try {
			byte[] encoded = ByteBuffer.allocate(X25519_X509_PREFIX.length + rawPublicKey.length)
					.put(X25519_X509_PREFIX)
					.put(rawPublicKey)
					.array();
			return KeyFactory.getInstance("X25519").generatePublic(new X509EncodedKeySpec(encoded));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Failed to decode X25519 public key", e);
		}
	}

	static boolean isValidFrameLength(int length) {
		return length > 0 && length <= MAX_FRAME_LENGTH;
	}

	private static byte[] deriveIv(long sequenceNumber) {
		return ByteBuffer.allocate(IV_LENGTH).putLong(sequenceNumber).array();
	}

	private static SecretKey deriveTrafficKey(SecretKey sessionKey, byte direction) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update("midnight-voice-direction".getBytes(StandardCharsets.UTF_8));
			digest.update(direction);
			digest.update(sessionKey.getEncoded());
			return new SecretKeySpec(digest.digest(), "AES");
		} catch (Exception e) {
			throw new IllegalStateException("Failed to derive directional traffic key", e);
		}
	}

	private static byte[] initX25519PublicKeyPrefix() {
		byte[] encoded = generateEcdhKeyPair().getPublic().getEncoded();
		if (encoded.length <= X25519_PUBLIC_KEY_LENGTH) {
			throw new IllegalStateException("Unexpected X25519 public key encoding length: " + encoded.length);
		}
		return Arrays.copyOf(encoded, encoded.length - X25519_PUBLIC_KEY_LENGTH);
	}
}
