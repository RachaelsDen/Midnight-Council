package dev.kgoodwin.midnightcouncil.voice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

class VoiceCodecTest {

	private VoiceCodec codec;

	@AfterEach
	void tearDown() {
		if (codec != null) {
			codec.close();
		}
	}

	@Test
	void roundTripPreservesSampleCount() {
		codec = new VoiceCodec();
		short[] pcm = generateSine(960, 48000, 440);

		byte[] encoded = codec.encode(pcm);
		short[] decoded = codec.decode(encoded);

		assertEquals(pcm.length, decoded.length);
	}

	@Test
	void roundTripMultipleFramesPreservesLength() {
		codec = new VoiceCodec();
		short[] frame = generateSine(960, 48000, 440);

		for (int i = 0; i < 10; i++) {
			byte[] encoded = codec.encode(frame);
			short[] decoded = codec.decode(encoded);
			assertEquals(frame.length, decoded.length);
		}
	}

	@Test
	void encodeProducesSmallerOutput() {
		codec = new VoiceCodec();
		short[] pcm = generateSine(960, 48000, 440);

		byte[] encoded = codec.encode(pcm);

		int pcmByteSize = pcm.length * 2;
		assertTrue(encoded.length < pcmByteSize,
			"Opus output (" + encoded.length + " bytes) should be smaller than PCM input (" + pcmByteSize + " bytes)");
	}

	@Test
	void decodeHandlesZeroLengthInput() {
		codec = new VoiceCodec();

		short[] decoded = codec.decode(new byte[0]);

		assertNotNull(decoded);
	}

	@Test
	void configurableBitrate() {
		codec = VoiceCodec.builder().bitrate(32000).build();
		short[] pcm = generateSine(960, 48000, 440);

		byte[] encoded = codec.encode(pcm);
		short[] decoded = codec.decode(encoded);

		assertEquals(pcm.length, decoded.length);
	}

	@Test
	void configurableFrameSize() {
		int frameSize = 480;
		codec = VoiceCodec.builder().frameSize(frameSize).build();
		short[] pcm = generateSine(frameSize, 48000, 440);

		byte[] encoded = codec.encode(pcm);
		short[] decoded = codec.decode(encoded);

		assertEquals(pcm.length, decoded.length);
	}

	@Test
	void closeIsIdempotent() {
		codec = new VoiceCodec();
		codec.close();
		codec.close();
		codec = null;
	}

	@Test
	void encodeAfterCloseThrows() {
		codec = new VoiceCodec();
		codec.close();
		codec = null;

		VoiceCodec closed = new VoiceCodec();
		closed.close();

		assertThrows(IllegalStateException.class, () -> closed.encode(new short[960]));
	}

	@Test
	void decodeAfterCloseThrows() {
		VoiceCodec closed = new VoiceCodec();
		closed.close();

		assertThrows(IllegalStateException.class, () -> closed.decode(new byte[10]));
	}

	@Test
	void multipleEncodeDecodeCyclesDoNotDegrade() {
		codec = new VoiceCodec();
		short[] pcm = generateSine(960, 48000, 440);

		byte[] firstEncoded = codec.encode(pcm);
		short[] firstDecoded = codec.decode(firstEncoded);

		for (int i = 0; i < 50; i++) {
			codec.encode(pcm);
			codec.decode(firstEncoded);
		}

		byte[] lastEncoded = codec.encode(pcm);
		short[] lastDecoded = codec.decode(lastEncoded);

		assertEquals(firstDecoded.length, lastDecoded.length,
			"Output length should not degrade over many cycles");
	}

	@Test
	void customSampleRateAndChannels() {
		codec = VoiceCodec.builder()
			.sampleRate(24000)
			.channels(2)
			.frameSize(480)
			.bitrate(48000)
			.build();
		short[] pcm = generateSine(480 * 2, 24000, 440);

		byte[] encoded = codec.encode(pcm);
		short[] decoded = codec.decode(encoded);

		assertEquals(pcm.length, decoded.length);
	}

	@Test
	void builderDefaultsMatchConstructorDefaults() {
		VoiceCodec a = new VoiceCodec();
		VoiceCodec b = VoiceCodec.builder().build();

		short[] pcm = generateSine(960, 48000, 440);
		byte[] encodedA = a.encode(pcm);
		byte[] encodedB = b.encode(pcm);

		assertEquals(encodedA.length, encodedB.length);

		a.close();
		b.close();
		codec = null;
	}

	private static short[] generateSine(int samples, int sampleRate, double frequencyHz) {
		short[] data = new short[samples];
		for (int i = 0; i < samples; i++) {
			double t = (double) i / sampleRate;
			data[i] = (short) (Short.MAX_VALUE * 0.5 * Math.sin(2.0 * Math.PI * frequencyHz * t));
		}
		return data;
	}
}
