package dev.kgoodwin.midnightcouncil.api.voice;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;
import dev.kgoodwin.midnightcouncil.api.Position;

public interface VoiceClientConnection {

	PlayerReference getPlayerId();

	MicrophoneState getMicrophoneState();

	void setMicrophoneState(MicrophoneState state);

	Position getPosition();

	boolean isConnected();

	void sendPacket(AudioPacket packet);

	long getLastPacketTime();
}
