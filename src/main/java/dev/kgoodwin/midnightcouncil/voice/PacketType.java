package dev.kgoodwin.midnightcouncil.voice;

enum PacketType {
	AUDIO((byte) 0x1),
	CONNECT((byte) 0x2),
	DISCONNECT((byte) 0x3),
	KEEPALIVE((byte) 0x4);

	final byte id;

	PacketType(byte id) {
		this.id = id;
	}

	static PacketType fromId(byte id) {
		for (PacketType t : values()) {
			if (t.id == id) {
				return t;
			}
		}
		throw new IllegalArgumentException("Unknown packet type: 0x" + Integer.toHexString(id));
	}

	static PacketType fromIdSafe(byte id) {
		for (PacketType t : values()) {
			if (t.id == id) {
				return t;
			}
		}
		return null;
	}
}
