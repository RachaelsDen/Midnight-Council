package dev.kgoodwin.midnightcouncil.voice;

import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusEncoder;
import io.github.jaredmdobson.concentus.OpusException;

import java.util.Objects;

public class VoiceCodec {

	static final int SAMPLE_RATE = 48000;
	static final int CHANNELS = 1;
	static final int FRAME_SIZE = 960;
	private static final int MAX_PACKET_BYTES = 4000;

	private final OpusEncoder encoder;
	private final OpusDecoder decoder;
	private final Object encoderLock = new Object();
	private final Object decoderLock = new Object();

	public VoiceCodec() {
		try {
			encoder = new OpusEncoder(SAMPLE_RATE, CHANNELS, OpusApplication.OPUS_APPLICATION_VOIP);
			decoder = new OpusDecoder(SAMPLE_RATE, CHANNELS);
		} catch (OpusException e) {
			throw new VoiceCodecException("Failed to initialize Opus codec", e);
		}
	}

	public byte[] encode(short[] pcmData) {
		Objects.requireNonNull(pcmData, "pcmData");
		if (pcmData.length < FRAME_SIZE) {
			throw new IllegalArgumentException(
				"pcmData must contain at least " + FRAME_SIZE + " samples, got " + pcmData.length);
		}
		byte[] buffer = new byte[MAX_PACKET_BYTES];
		int encodedLength;
		synchronized (encoderLock) {
			encodedLength = encodeInternal(pcmData, buffer);
		}
		byte[] result = new byte[encodedLength];
		System.arraycopy(buffer, 0, result, 0, encodedLength);
		return result;
	}

	public short[] decode(byte[] encodedData) {
		Objects.requireNonNull(encodedData, "encodedData");
		short[] buffer = new short[FRAME_SIZE * CHANNELS];
		int samplesDecoded;
		synchronized (decoderLock) {
			samplesDecoded = decodeInternal(encodedData, buffer);
		}
		if (samplesDecoded < buffer.length) {
			short[] trimmed = new short[samplesDecoded];
			System.arraycopy(buffer, 0, trimmed, 0, samplesDecoded);
			return trimmed;
		}
		return buffer;
	}

	private int encodeInternal(short[] pcmData, byte[] output) {
		try {
			return encoder.encode(pcmData, 0, FRAME_SIZE, output, 0, output.length);
		} catch (OpusException e) {
			throw new VoiceCodecException("Opus encoding failed", e);
		}
	}

	private int decodeInternal(byte[] encodedData, short[] output) {
		try {
			return decoder.decode(encodedData, 0, encodedData.length, output, 0, FRAME_SIZE, false);
		} catch (OpusException e) {
			throw new VoiceCodecException("Opus decoding failed", e);
		}
	}

	public static class VoiceCodecException extends RuntimeException {
		public VoiceCodecException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
