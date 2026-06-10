package dev.kgoodwin.midnightcouncil.api.voice;

import java.util.Collection;

public interface VoiceServer {

	void start(int port);

	void stop();

	void connect(VoiceClientConnection connection);

	void disconnect(VoiceClientConnection connection);

	Collection<VoiceClientConnection> getConnections();

	void sendAudio(AudioPacket packet);

	boolean isRunning();
}
