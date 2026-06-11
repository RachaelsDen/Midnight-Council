package dev.kgoodwin.midnightcouncil.voice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;
import dev.kgoodwin.midnightcouncil.api.voice.AudioPacket;
import dev.kgoodwin.midnightcouncil.api.voice.MicrophoneState;
import dev.kgoodwin.midnightcouncil.api.voice.VoiceClientConnection;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoiceClientServiceTest {

	private VoiceCodec codec;
	private VoiceClientService service;
	private static final PlayerReference PLAYER = PlayerReference.ofName("TestPlayer");
	private static final PlayerReference OTHER_PLAYER = PlayerReference.ofName("OtherPlayer");

	@BeforeEach
	void setUp() {
		codec = new VoiceCodec();
		service = new VoiceClientService(codec);
	}

	private static short[] generateSilence() {
		return new short[VoiceCodec.FRAME_SIZE];
	}

	private static short[] generateSineWave(int samples, double frequency) {
		short[] pcm = new short[samples];
		for (int i = 0; i < samples; i++) {
			pcm[i] = (short) (Short.MAX_VALUE * 0.5 * Math.sin(2 * Math.PI * frequency * i / VoiceCodec.SAMPLE_RATE));
		}
		return pcm;
	}

	@Test
	void implementsVoiceClientConnection() {
		assertTrue(service instanceof VoiceClientConnection);
	}

	@Test
	void startsDisconnected() {
		assertFalse(service.isConnected());
	}

	@Test
	void connectSetsConnected() {
		service.connect(PLAYER);
		assertTrue(service.isConnected());
	}

	@Test
	void connectSetsPlayerId() {
		service.connect(PLAYER);
		assertEquals(PLAYER, service.getPlayerId());
	}

	@Test
	void disconnectClearsConnected() {
		service.connect(PLAYER);
		service.disconnect();
		assertFalse(service.isConnected());
	}

	@Test
	void disconnectClearsPlayerId() {
		service.connect(PLAYER);
		service.disconnect();
		assertEquals(null, service.getPlayerId());
	}

	@Test
	void connectWhenAlreadyConnectedThrows() {
		service.connect(PLAYER);
		assertThrows(IllegalStateException.class, () -> service.connect(PLAYER));
	}

	@Test
	void disconnectWhenNotConnectedThrows() {
		assertThrows(IllegalStateException.class, () -> service.disconnect());
	}

	@Test
	void connectClearsPendingAudio() {
		service.connect(PLAYER);
		service.receiveAudio(new AudioPacket(PLAYER, codec.encode(generateSilence()), 0L, 0L));
		assertFalse(service.getPendingAudio().isEmpty());

		service.disconnect();
		service.connect(PLAYER);
		assertTrue(service.getPendingAudio().isEmpty());
	}

	@Test
	void sendAudioReturnsAudioPacket() {
		service.connect(PLAYER);
		service.setMicrophoneState(MicrophoneState.ACTIVE);
		short[] pcm = generateSilence();
		AudioPacket packet = service.sendAudio(pcm);

		assertNotNull(packet);
		assertEquals(PLAYER, packet.senderId());
		assertNotNull(packet.encodedData());
		assertTrue(packet.encodedData().length > 0);
	}

	@Test
	void sendAudioEncodesData() {
		service.connect(PLAYER);
		service.setMicrophoneState(MicrophoneState.ACTIVE);
		short[] pcm = generateSineWave(VoiceCodec.FRAME_SIZE, 440.0);
		AudioPacket packet = service.sendAudio(pcm);

		assertNotNull(packet.encodedData());
		assertTrue(packet.encodedData().length > 0);
	}

	@Test
	void sendAudioSequenceNumbersIncrement() {
		service.connect(PLAYER);
		service.setMicrophoneState(MicrophoneState.ACTIVE);
		short[] pcm = generateSilence();

		AudioPacket first = service.sendAudio(pcm);
		AudioPacket second = service.sendAudio(pcm);
		AudioPacket third = service.sendAudio(pcm);

		assertEquals(0L, first.sequenceNumber());
		assertEquals(1L, second.sequenceNumber());
		assertEquals(2L, third.sequenceNumber());
	}

	@Test
	void sendAudioTimestampIsCurrentTime() {
		service.connect(PLAYER);
		service.setMicrophoneState(MicrophoneState.ACTIVE);
		long before = System.currentTimeMillis();
		AudioPacket packet = service.sendAudio(generateSilence());
		long after = System.currentTimeMillis();

		assertTrue(packet.timestamp() >= before);
		assertTrue(packet.timestamp() <= after);
	}

	@Test
	void sendAudioUpdatesLastPacketTime() {
		service.connect(PLAYER);
		service.setMicrophoneState(MicrophoneState.ACTIVE);
		assertEquals(0L, service.getLastPacketTime());

		long before = System.currentTimeMillis();
		service.sendAudio(generateSilence());
		long after = System.currentTimeMillis();

		assertTrue(service.getLastPacketTime() >= before);
		assertTrue(service.getLastPacketTime() <= after);
	}

	@Test
	void sendAudioWhenDisconnectedThrows() {
		assertThrows(IllegalStateException.class, () -> service.sendAudio(generateSilence()));
	}

	@Test
	void sendAudioWhenMutedReturnsNull() {
		service.connect(PLAYER);

		AudioPacket packet = service.sendAudio(generateSilence());

		assertNull(packet);
	}

	@Test
	void sendAudioWhenMutedDoesNotAdvanceSequence() {
		service.connect(PLAYER);

		assertNull(service.sendAudio(generateSilence()));

		service.setMicrophoneState(MicrophoneState.ACTIVE);
		AudioPacket packet = service.sendAudio(generateSilence());
		assertEquals(0L, packet.sequenceNumber());
	}

	@Test
	void sendAudioWhenMutedDoesNotUpdateLastPacketTime() {
		service.connect(PLAYER);

		assertNull(service.sendAudio(generateSilence()));
		assertEquals(0L, service.getLastPacketTime());
	}

	@Test
	void sendAudioWhenPushToTalkReturnsAudioPacket() {
		service.connect(PLAYER);
		service.setMicrophoneState(MicrophoneState.PUSH_TO_TALK);

		AudioPacket packet = service.sendAudio(generateSilence());

		assertNotNull(packet);
	}

	@Test
	void sendAudioRejectsOversizedPcmBuffer() {
		service.connect(PLAYER);
		service.setMicrophoneState(MicrophoneState.ACTIVE);

		assertThrows(IllegalArgumentException.class,
			() -> service.sendAudio(new short[VoiceCodec.FRAME_SIZE + 1]));
	}

	@Test
	void receiveAudioReturnsDecodedPcm() {
		service.connect(PLAYER);
		byte[] encoded = codec.encode(generateSineWave(VoiceCodec.FRAME_SIZE, 440.0));
		AudioPacket packet = new AudioPacket(PLAYER, encoded, 0L, 0L);

		short[] result = service.receiveAudio(packet);

		assertNotNull(result);
		assertEquals(VoiceCodec.FRAME_SIZE, result.length);
	}

	@Test
	void receiveAudioBuffersForPlayback() {
		service.connect(PLAYER);
		byte[] encoded = codec.encode(generateSilence());

		service.receiveAudio(new AudioPacket(PLAYER, encoded, 0L, 0L));
		service.receiveAudio(new AudioPacket(PLAYER, encoded, 1L, 1L));

		List<short[]> pending = service.getPendingAudio();
		assertEquals(2, pending.size());
	}

	@Test
	void receiveAudioUsesSeparateDecoderStatePerSender() {
		service.connect(PLAYER);

		try (VoiceCodec senderCodecA = new VoiceCodec();
				VoiceCodec senderCodecB = new VoiceCodec();
				VoiceCodec expectedDecoderA = new VoiceCodec();
				VoiceCodec expectedDecoderB = new VoiceCodec()) {
			AudioPacket packetA1 = new AudioPacket(PLAYER, senderCodecA.encode(generateSineWave(VoiceCodec.FRAME_SIZE, 440.0)), 0L, 0L);
			AudioPacket packetB1 = new AudioPacket(OTHER_PLAYER, senderCodecB.encode(generateSineWave(VoiceCodec.FRAME_SIZE, 660.0)), 0L, 1L);
			AudioPacket packetA2 = new AudioPacket(PLAYER, senderCodecA.encode(generateSineWave(VoiceCodec.FRAME_SIZE, 554.37)), 1L, 2L);
			AudioPacket packetB2 = new AudioPacket(OTHER_PLAYER, senderCodecB.encode(generateSineWave(VoiceCodec.FRAME_SIZE, 880.0)), 1L, 3L);

			short[] expectedA1 = expectedDecoderA.decode(packetA1.encodedData());
			short[] expectedB1 = expectedDecoderB.decode(packetB1.encodedData());
			short[] expectedA2 = expectedDecoderA.decode(packetA2.encodedData());
			short[] expectedB2 = expectedDecoderB.decode(packetB2.encodedData());

			assertArrayEquals(expectedA1, service.receiveAudio(packetA1));
			assertArrayEquals(expectedB1, service.receiveAudio(packetB1));
			assertArrayEquals(expectedA2, service.receiveAudio(packetA2));
			assertArrayEquals(expectedB2, service.receiveAudio(packetB2));
		}
	}

	@Test
	void receiveAudioDropsOldestFramesWhenBufferIsFull() {
		service.connect(PLAYER);
		List<short[]> decodedFrames = new java.util.ArrayList<>();

		for (int i = 0; i < VoiceClientService.MAX_PENDING_FRAMES + 3; i++) {
			short[] pcm = generateSineWave(VoiceCodec.FRAME_SIZE, 440.0 + (i * 25.0));
			byte[] encoded = codec.encode(pcm);
			decodedFrames.add(service.receiveAudio(new AudioPacket(PLAYER, encoded, i, i)));
		}

		List<short[]> pending = service.getPendingAudio();
		assertEquals(VoiceClientService.MAX_PENDING_FRAMES, pending.size());

		int offset = decodedFrames.size() - VoiceClientService.MAX_PENDING_FRAMES;
		for (int i = 0; i < pending.size(); i++) {
			assertArrayEquals(decodedFrames.get(offset + i), pending.get(i));
		}
	}

	@Test
	void receiveAudioWhenDisconnectedReturnsNull() {
		byte[] encoded = codec.encode(generateSilence());
		AudioPacket packet = new AudioPacket(PLAYER, encoded, 0L, 0L);

		short[] result = service.receiveAudio(packet);
		assertEquals(null, result);
	}

	@Test
	void receiveAudioWhenDisconnectedDoesNotBuffer() {
		byte[] encoded = codec.encode(generateSilence());
		AudioPacket packet = new AudioPacket(PLAYER, encoded, 0L, 0L);

		service.receiveAudio(packet);
		assertTrue(service.getPendingAudio().isEmpty());
	}

	@Test
	void receiveAudioUpdatesLastPacketTime() {
		service.connect(PLAYER);
		byte[] encoded = codec.encode(generateSilence());

		long before = System.currentTimeMillis();
		service.receiveAudio(new AudioPacket(PLAYER, encoded, 0L, 0L));
		long after = System.currentTimeMillis();

		assertTrue(service.getLastPacketTime() >= before);
		assertTrue(service.getLastPacketTime() <= after);
	}

	@Test
	void getPendingAudioReturnsCopy() {
		service.connect(PLAYER);
		byte[] encoded = codec.encode(generateSilence());
		service.receiveAudio(new AudioPacket(PLAYER, encoded, 0L, 0L));

		List<short[]> first = service.getPendingAudio();
		List<short[]> second = service.getPendingAudio();

		assertEquals(first.size(), second.size());
		assertFalse(first == second);
	}

	@Test
	void clearPendingAudioEmptiesBuffer() {
		service.connect(PLAYER);
		byte[] encoded = codec.encode(generateSilence());
		service.receiveAudio(new AudioPacket(PLAYER, encoded, 0L, 0L));
		assertFalse(service.getPendingAudio().isEmpty());

		service.clearPendingAudio();
		assertTrue(service.getPendingAudio().isEmpty());
	}

	@Test
	void microphoneStateDefaultsToMuted() {
		assertEquals(MicrophoneState.MUTED, service.getMicrophoneState());
	}

	@Test
	void setMicrophoneStateUpdatesState() {
		service.setMicrophoneState(MicrophoneState.ACTIVE);
		assertEquals(MicrophoneState.ACTIVE, service.getMicrophoneState());

		service.setMicrophoneState(MicrophoneState.PUSH_TO_TALK);
		assertEquals(MicrophoneState.PUSH_TO_TALK, service.getMicrophoneState());

		service.setMicrophoneState(MicrophoneState.MUTED);
		assertEquals(MicrophoneState.MUTED, service.getMicrophoneState());
	}

	@Test
	void setMicrophoneStateNullThrows() {
		assertThrows(NullPointerException.class, () -> service.setMicrophoneState(null));
	}

	@Test
	void positionDefaultsToOrigin() {
		Position pos = service.getPosition();
		assertEquals(new Position(0, 0, 0), pos);
	}

	@Test
	void setPositionUpdatesPosition() {
		Position newPos = new Position(100.5, 64.0, -200.3);
		service.setPosition(newPos);
		assertEquals(newPos, service.getPosition());
	}

	@Test
	void setPositionNullThrows() {
		assertThrows(NullPointerException.class, () -> service.setPosition(null));
	}

	@Test
	void sendPacketDelegatesToReceiveAudio() {
		service.connect(PLAYER);
		byte[] encoded = codec.encode(generateSilence());
		AudioPacket packet = new AudioPacket(PLAYER, encoded, 0L, 0L);

		service.sendPacket(packet);

		List<short[]> pending = service.getPendingAudio();
		assertEquals(1, pending.size());
		assertEquals(VoiceCodec.FRAME_SIZE, pending.get(0).length);
	}

	@Test
	void roundTripSendAndReceive() {
		VoiceClientService sender = new VoiceClientService(codec);
		VoiceClientService receiver = new VoiceClientService(codec);

		PlayerReference senderId = PlayerReference.ofName("Sender");
		PlayerReference receiverId = PlayerReference.ofName("Receiver");

		sender.connect(senderId);
		receiver.connect(receiverId);
		sender.setMicrophoneState(MicrophoneState.ACTIVE);

		short[] original = generateSineWave(VoiceCodec.FRAME_SIZE, 440.0);
		AudioPacket packet = sender.sendAudio(original);

		assertEquals(senderId, packet.senderId());
		assertEquals(0L, packet.sequenceNumber());
		assertTrue(packet.encodedData().length > 0);

		short[] decoded = receiver.receiveAudio(packet);
		assertNotNull(decoded);
		assertEquals(VoiceCodec.FRAME_SIZE, decoded.length);

		List<short[]> pending = receiver.getPendingAudio();
		assertEquals(1, pending.size());
		assertArrayEquals(decoded, pending.get(0));
	}

	@Test
	void roundTripPreservesAudioContent() {
		try (VoiceCodec senderCodec = new VoiceCodec();
				VoiceCodec receiverCodec = new VoiceCodec()) {
			short[] original = generateSineWave(VoiceCodec.FRAME_SIZE, 440.0);
			byte[] encoded = senderCodec.encode(original);
			short[] decoded = receiverCodec.decode(encoded);

			assertEquals(original.length, decoded.length);

			double energyOriginal = 0;
			double energyDecoded = 0;
			for (int i = 0; i < original.length; i++) {
				energyOriginal += original[i] * original[i];
				energyDecoded += decoded[i] * decoded[i];
			}
			assertTrue(energyOriginal > 0, "Original signal should have energy");
			assertTrue(energyDecoded > 0, "Decoded signal should have energy");
		}
	}

	@Test
	void sequenceResetsOnReconnect() {
		service.connect(PLAYER);
		service.setMicrophoneState(MicrophoneState.ACTIVE);
		service.sendAudio(generateSilence());
		service.sendAudio(generateSilence());
		service.disconnect();

		service.connect(PLAYER);
		service.setMicrophoneState(MicrophoneState.ACTIVE);
		AudioPacket packet = service.sendAudio(generateSilence());
		assertEquals(0L, packet.sequenceNumber());
	}

	@Test
	void connectResetsLastPacketTime() {
		service.connect(PLAYER);
		service.setMicrophoneState(MicrophoneState.ACTIVE);
		service.sendAudio(generateSilence());
		assertTrue(service.getLastPacketTime() > 0L);

		service.disconnect();
		service.connect(PLAYER);

		assertEquals(0L, service.getLastPacketTime());
	}
}
