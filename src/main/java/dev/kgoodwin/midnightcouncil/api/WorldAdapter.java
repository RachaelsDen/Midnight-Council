package dev.kgoodwin.midnightcouncil.api;

import java.util.Optional;

public interface WorldAdapter extends PlatformInterface {

	void setBlock(Position position, String blockType);

	void clearBlock(Position position);

	String getBlockType(Position position);

	void spawnEntity(String entityType, Position position);

	Optional<Position> getPlayerPosition(PlayerReference playerReference);

	void playSound(Position position, String soundId, float volume, float pitch);

	void spawnParticles(String particleId, Position position, int count);
}
