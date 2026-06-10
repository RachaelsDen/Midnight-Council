package dev.kgoodwin.midnightcouncil.api.game;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public class PersistenceManager {

	private static final int FORMAT_VERSION = 1;

	public void saveToFile(GameState state, Path file) throws IOException {
		Path parent = file.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(file, serialize(state), StandardCharsets.UTF_8);
	}

	public GameState loadFromFile(Path file) throws IOException {
		String json = Files.readString(file, StandardCharsets.UTF_8);
		return deserialize(json);
	}

	private static String serialize(GameState state) {
		StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		sb.append("  \"version\": ").append(FORMAT_VERSION).append(",\n");
		sb.append("  \"phase\": \"").append(state.getPhase().name()).append("\",\n");
		sb.append("  \"dayCount\": ").append(state.getDayCount()).append(",\n");
		sb.append("  \"nightCount\": ").append(state.getNightCount()).append(",\n");
		appendNullableInt(sb, "nominatedSeat", state.getNominatedSeat());
		appendNullableInt(sb, "markedSeat", state.getMarkedSeat());
		sb.append("  \"timerActive\": ").append(state.isTimerActive()).append(",\n");
		sb.append("  \"players\": [");

		Collection<PlayerEntry> players = state.getPlayers().getPlayers();
		if (players.isEmpty()) {
			sb.append("]\n");
		} else {
			sb.append("\n");
			Iterator<PlayerEntry> it = players.iterator();
			while (it.hasNext()) {
				PlayerEntry entry = it.next();
				sb.append("    {\n");
				sb.append("      \"seatNumber\": ").append(entry.getSeatNumber()).append(",\n");
				sb.append("      \"displayName\": \"").append(escapeJson(entry.getDisplayName())).append("\",\n");
				sb.append("      \"lifeState\": \"").append(entry.getLifeState().name()).append("\",\n");
				sb.append("      \"sleepState\": \"").append(entry.getSleepState().name()).append("\",\n");
				sb.append("      \"storyteller\": ").append(entry.isStoryteller()).append(",\n");
				sb.append("      \"playerRef\": \"").append(escapeJson(entry.getPlayerReference().value())).append("\"\n");
				sb.append("    }");
				if (it.hasNext()) {
					sb.append(",");
				}
				sb.append("\n");
			}
			sb.append("  ]\n");
		}

		sb.append("}");
		return sb.toString();
	}

	private static void appendNullableInt(StringBuilder sb, String key, OptionalInt value) {
		sb.append("  \"").append(key).append("\": ");
		if (value.isPresent()) {
			sb.append(value.getAsInt());
		} else {
			sb.append("null");
		}
		sb.append(",\n");
	}

	private static String escapeJson(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		return sb.toString();
	}

	private static GameState deserialize(String json) throws IOException {
		JsonReader reader = new JsonReader(json);
		Map<String, Object> root = reader.readObject();

		int version = requireInt(root, "version");
		if (version != FORMAT_VERSION) {
			throw new IOException("Unsupported persistence format version: " + version);
		}

		GamePhase phase = GamePhase.valueOf(requireString(root, "phase"));
		int dayCount = requireInt(root, "dayCount");
		int nightCount = requireInt(root, "nightCount");
		Integer nominatedSeat = optionalInt(root, "nominatedSeat");
		Integer markedSeat = optionalInt(root, "markedSeat");
		boolean timerActive = requireBoolean(root, "timerActive");

		GameState state = GameState.reconstruct(phase, dayCount, nightCount, nominatedSeat, markedSeat, timerActive);

		List<Object> playersArray = requireList(root, "players");
		for (Object playerObj : playersArray) {
			@SuppressWarnings("unchecked")
			Map<String, Object> playerMap = (Map<String, Object>) playerObj;
			int seatNumber = requireInt(playerMap, "seatNumber");
			String displayName = requireString(playerMap, "displayName");
			LifeState lifeState = LifeState.valueOf(requireString(playerMap, "lifeState"));
			SleepState sleepState = SleepState.valueOf(requireString(playerMap, "sleepState"));
			boolean storyteller = requireBoolean(playerMap, "storyteller");
			String playerRef = requireString(playerMap, "playerRef");

			state.getPlayers().register(
					new PlayerEntry(seatNumber, displayName, lifeState, sleepState, storyteller, PlayerReference.ofName(playerRef)));
		}

		return state;
	}

	private static int requireInt(Map<String, Object> map, String key) throws IOException {
		Object value = map.get(key);
		if (value instanceof Number n) {
			return n.intValue();
		}
		throw new IOException("Expected integer for key '" + key + "'");
	}

	private static String requireString(Map<String, Object> map, String key) throws IOException {
		Object value = map.get(key);
		if (value instanceof String s) {
			return s;
		}
		throw new IOException("Expected string for key '" + key + "'");
	}

	private static boolean requireBoolean(Map<String, Object> map, String key) throws IOException {
		Object value = map.get(key);
		if (value instanceof Boolean b) {
			return b;
		}
		throw new IOException("Expected boolean for key '" + key + "'");
	}

	private static Integer optionalInt(Map<String, Object> map, String key) throws IOException {
		Object value = map.get(key);
		if (value == null) {
			return null;
		}
		if (value instanceof Number n) {
			return n.intValue();
		}
		throw new IOException("Expected integer or null for key '" + key + "'");
	}

	@SuppressWarnings("unchecked")
	private static List<Object> requireList(Map<String, Object> map, String key) throws IOException {
		Object value = map.get(key);
		if (value instanceof List<?>) {
			return (List<Object>) value;
		}
		throw new IOException("Expected array for key '" + key + "'");
	}

	private static class JsonReader {
		private final String json;
		private int pos;

		JsonReader(String json) {
			this.json = json;
			this.pos = 0;
		}

		void skipWhitespace() {
			while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
				pos++;
			}
		}

		void expect(char c) throws IOException {
			skipWhitespace();
			if (pos >= json.length() || json.charAt(pos) != c) {
				throw new IOException("Expected '" + c + "' at position " + pos);
			}
			pos++;
		}

		Map<String, Object> readObject() throws IOException {
			Map<String, Object> map = new LinkedHashMap<>();
			expect('{');
			skipWhitespace();

			if (pos < json.length() && json.charAt(pos) == '}') {
				pos++;
				return map;
			}

			while (true) {
				skipWhitespace();
				String key = readString();
				skipWhitespace();
				expect(':');
				Object value = readValue();
				map.put(key, value);
				skipWhitespace();

				if (pos < json.length() && json.charAt(pos) == ',') {
					pos++;
				} else {
					break;
				}
			}

			skipWhitespace();
			expect('}');
			return map;
		}

		List<Object> readArray() throws IOException {
			List<Object> list = new ArrayList<>();
			expect('[');
			skipWhitespace();

			if (pos < json.length() && json.charAt(pos) == ']') {
				pos++;
				return list;
			}

			while (true) {
				list.add(readValue());
				skipWhitespace();

				if (pos < json.length() && json.charAt(pos) == ',') {
					pos++;
				} else {
					break;
				}
			}

			skipWhitespace();
			expect(']');
			return list;
		}

		Object readValue() throws IOException {
			skipWhitespace();
			if (pos >= json.length()) {
				throw new IOException("Unexpected end of JSON");
			}

			char c = json.charAt(pos);
			if (c == '"') {
				return readString();
			}
			if (c == '{') {
				return readObject();
			}
			if (c == '[') {
				return readArray();
			}
			if (c == 't' || c == 'f') {
				return readBoolean();
			}
			if (c == 'n') {
				return readNull();
			}
			if (c == '-' || Character.isDigit(c)) {
				return readNumber();
			}

			throw new IOException("Unexpected character '" + c + "' at position " + pos);
		}

		String readString() throws IOException {
			skipWhitespace();
			if (pos >= json.length() || json.charAt(pos) != '"') {
				throw new IOException("Expected '\"' at position " + pos);
			}
			pos++;

			StringBuilder sb = new StringBuilder();
			while (pos < json.length()) {
				char c = json.charAt(pos++);
				if (c == '"') {
					return sb.toString();
				}
				if (c == '\\') {
					if (pos >= json.length()) {
						throw new IOException("Unexpected end of string escape");
					}
					char escaped = json.charAt(pos++);
					switch (escaped) {
						case '"' -> sb.append('"');
						case '\\' -> sb.append('\\');
						case 'n' -> sb.append('\n');
						case 't' -> sb.append('\t');
						case 'r' -> sb.append('\r');
						case '/' -> sb.append('/');
						case 'u' -> {
							if (pos + 4 > json.length()) {
								throw new IOException("Incomplete unicode escape");
							}
							String hex = json.substring(pos, pos + 4);
							pos += 4;
							sb.append((char) Integer.parseInt(hex, 16));
						}
						default -> throw new IOException("Unknown escape sequence: \\" + escaped);
					}
				} else {
					sb.append(c);
				}
			}

			throw new IOException("Unterminated string");
		}

		Boolean readBoolean() throws IOException {
			if (json.startsWith("true", pos)) {
				pos += 4;
				return true;
			}
			if (json.startsWith("false", pos)) {
				pos += 5;
				return false;
			}
			throw new IOException("Expected boolean at position " + pos);
		}

		Object readNull() throws IOException {
			if (json.startsWith("null", pos)) {
				pos += 4;
				return null;
			}
			throw new IOException("Expected null at position " + pos);
		}

		Number readNumber() throws IOException {
			int start = pos;
			if (json.charAt(pos) == '-') {
				pos++;
			}
			while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
				pos++;
			}
			if (pos < json.length() && json.charAt(pos) == '.') {
				pos++;
				while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
					pos++;
				}
			}
			if (pos < json.length() && (json.charAt(pos) == 'e' || json.charAt(pos) == 'E')) {
				pos++;
				if (pos < json.length() && (json.charAt(pos) == '+' || json.charAt(pos) == '-')) {
					pos++;
				}
				while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
					pos++;
				}
			}

			String numStr = json.substring(start, pos);
			if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
				return Double.parseDouble(numStr);
			}
			return Integer.parseInt(numStr);
		}
	}
}
