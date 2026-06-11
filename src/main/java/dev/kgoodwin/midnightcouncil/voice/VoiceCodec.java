package dev.kgoodwin.midnightcouncil.voice;

import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusEncoder;

import java.util.Arrays;

public final class VoiceCodec implements AutoCloseable {

	private static final int DEFAULT_SAMPLE_RATE = 48000;
	private static final int DEFAULT_CHANNELS = 1;
	private static final int DEFAULT_BITRATE = 64000;
	private static final int DEFAULT_FRAME_SIZE = 960;
	private static final int MAX_OPUS_PAYLOAD = 4000;

	static final int SAMPLE_RATE = DEFAULT_SAMPLE_RATE;
	static final int CHANNELS = DEFAULT_CHANNELS;
	static final int FRAME_SIZE = DEFAULT_FRAME_SIZE;

	private final int sampleRate;
	private final int channels;
	private final int frameSize;

	private final Object lock = new Object();
	private OpusEncoder encoder;
	private OpusDecoder decoder;
	private boolean closed;

	public VoiceCodec() {
		this(DEFAULT_SAMPLE_RATE, DEFAULT_CHANNELS, DEFAULT_BITRATE, DEFAULT_FRAME_SIZE);
	}

	private VoiceCodec(int sampleRate, int channels, int bitrate, int frameSize) {
		this.sampleRate = sampleRate;
		this.channels = channels;
		this.frameSize = frameSize;
		try {
			encoder = new OpusEncoder(sampleRate, channels, OpusApplication.OPUS_APPLICATION_VOIP);
			encoder.setBitrate(bitrate);
			decoder = new OpusDecoder(sampleRate, channels);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to initialize Opus codec", e);
		}
	}

	public byte[] encode(short[] pcmData) {
		synchronized (lock) {
			requireOpen();
			byte[] outputBuffer = new byte[MAX_OPUS_PAYLOAD];
			try {
				int encodedBytes = encoder.encode(pcmData, 0, frameSize, outputBuffer, 0, outputBuffer.length);
				return Arrays.copyOf(outputBuffer, encodedBytes);
			} catch (Exception e) {
				throw new IllegalStateException("Opus encoding failed", e);
			}
		}
	}

	public short[] decode(byte[] opusData) {
		synchronized (lock) {
			requireOpen();
			short[] outputBuffer = new short[frameSize * channels];
			try {
				int decodedSamples = decoder.decode(opusData, 0, opusData.length, outputBuffer, 0, frameSize, false);
				return Arrays.copyOf(outputBuffer, decodedSamples * channels);
			} catch (Exception e) {
				throw new IllegalStateException("Opus decoding failed", e);
			}
		}
	}

	@Override
	public void close() {
		synchronized (lock) {
			encoder = null;
			decoder = null;
			closed = true;
		}
	}

	private void requireOpen() {
		if (closed) {
			throw new IllegalStateException("VoiceCodec has been closed");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private int sampleRate = DEFAULT_SAMPLE_RATE;
		private int channels = DEFAULT_CHANNELS;
		private int bitrate = DEFAULT_BITRATE;
		private int frameSize = DEFAULT_FRAME_SIZE;

		public Builder sampleRate(int sampleRate) {
			this.sampleRate = sampleRate;
			return this;
		}

		public Builder channels(int channels) {
			this.channels = channels;
			return this;
		}

		public Builder bitrate(int bitrate) {
			this.bitrate = bitrate;
			return this;
		}

		public Builder frameSize(int frameSize) {
			this.frameSize = frameSize;
			return this;
		}

		public VoiceCodec build() {
			return new VoiceCodec(sampleRate, channels, bitrate, frameSize);
		}
	}
}
