package dev.kgoodwin.midnightcouncil.api.voice;

import dev.kgoodwin.midnightcouncil.api.PlayerReference;

import java.util.Arrays;
import java.util.Objects;

public record AudioPacket(PlayerReference senderId, byte[] encodedData, long sequenceNumber, long timestamp) {

	public AudioPacket {
		Objects.requireNonNull(senderId, "senderId");
		Objects.requireNonNull(encodedData, "encodedData");
	}

	public int length() {
		return encodedData.length;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof AudioPacket that)) return false;
		return sequenceNumber == that.sequenceNumber
			&& timestamp == that.timestamp
			&& senderId.equals(that.senderId)
			&& Arrays.equals(encodedData, that.encodedData);
	}

	@Override
	public int hashCode() {
		int result = senderId.hashCode();
		result = 31 * result + Arrays.hashCode(encodedData);
		result = 31 * result + Long.hashCode(sequenceNumber);
		result = 31 * result + Long.hashCode(timestamp);
		return result;
	}
}
