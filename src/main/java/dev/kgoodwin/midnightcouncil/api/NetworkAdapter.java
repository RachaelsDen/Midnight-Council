package dev.kgoodwin.midnightcouncil.api;

public interface NetworkAdapter extends PlatformInterface {

	void broadcastPublicPayload(String channel, byte[] payload);

	void sendStorytellerPayload(PlayerReference storyteller, String channel, byte[] payload);

	void registerReceiver(String channel, PayloadHandler handler);

	@FunctionalInterface
	interface PayloadHandler {
		void handle(PlayerReference playerReference, String channel, byte[] payload);
	}
}
