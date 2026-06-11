package dev.kgoodwin.midnightcouncil.api.game;

import dev.kgoodwin.midnightcouncil.api.GamePhase;
import dev.kgoodwin.midnightcouncil.api.PlayerReference;

import java.io.IOException;
import java.math.BigDecimal;
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
	private static final int MAX_JSON_NESTING_DEPTH = 64;

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
		reader.ensureFullyConsumed();

		int version = requireInt(root, "version");
		if (version != FORMAT_VERSION) {
			throw new IOException("Unsupported persistence format version: " + version);
		}

		GamePhase phase = parseEnum(GamePhase.class, requireString(root, "phase"), "phase");
		int dayCount = requireInt(root, "dayCount");
		int nightCount = requireInt(root, "nightCount");
		Integer nominatedSeat = optionalInt(root, "nominatedSeat");
		Integer markedSeat = optionalInt(root, "markedSeat");
		boolean timerActive = requireBoolean(root, "timerActive");
		validateNonNegative(dayCount, "dayCount");
		validateNonNegative(nightCount, "nightCount");
		validateOptionalNonNegative(nominatedSeat, "nominatedSeat");
		validateOptionalNonNegative(markedSeat, "markedSeat");
		validatePhaseState(phase, nominatedSeat, markedSeat);

		GameState state = GameState.reconstruct(phase, dayCount, nightCount, nominatedSeat, markedSeat, timerActive);

		List<Object> playersArray = requireList(root, "players");
		for (Object playerObj : playersArray) {
			Map<String, Object> playerMap = requireObject(playerObj, "players element");
			int seatNumber = requireInt(playerMap, "seatNumber");
			String displayName = requireString(playerMap, "displayName");
			LifeState lifeState = parseEnum(LifeState.class, requireString(playerMap, "lifeState"), "lifeState");
			SleepState sleepState = parseEnum(SleepState.class, requireString(playerMap, "sleepState"), "sleepState");
			boolean storyteller = requireBoolean(playerMap, "storyteller");
			String playerRef = requireString(playerMap, "playerRef");

			try {
				state.getPlayers().register(
						new PlayerEntry(seatNumber, displayName, lifeState, sleepState, storyteller, PlayerReference.ofName(playerRef)));
			} catch (IllegalArgumentException e) {
				throw new IOException("Invalid player entry in persistence data", e);
			}
		}

		if (nominatedSeat != null && state.getPlayers().getBySeatNumber(nominatedSeat).isEmpty()) {
			throw new IOException("nominatedSeat does not reference a loaded player seat: " + nominatedSeat);
		}
		if (markedSeat != null && state.getPlayers().getBySeatNumber(markedSeat).isEmpty()) {
			throw new IOException("markedSeat does not reference a loaded player seat: " + markedSeat);
		}

		return state;
	}

	private static <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, String key) throws IOException {
		try {
			return Enum.valueOf(enumType, value);
		} catch (IllegalArgumentException e) {
			throw new IOException("Invalid " + key + " value: " + value, e);
		}
	}

	private static int requireInt(Map<String, Object> map, String key) throws IOException {
		Object value = map.get(key);
		if (value instanceof Number n) {
			return requireIntegralNumber(n, key);
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
			return requireIntegralNumber(n, key);
		}
		throw new IOException("Expected integer or null for key '" + key + "'");
	}

	private static int requireIntegralNumber(Number value, String key) throws IOException {
		if (value instanceof BigDecimal bigDecimal) {
			BigDecimal normalized = bigDecimal.stripTrailingZeros();
			if (normalized.scale() > 0) {
				throw new IOException("Expected integer for key '" + key + "'");
			}
			if (normalized.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0
					|| normalized.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
				throw new IOException("Integer value out of range for key '" + key + "'");
			}
			return normalized.intValueExact();
		}
		double numericValue = value.doubleValue();
		if (!Double.isFinite(numericValue) || numericValue != Math.rint(numericValue)) {
			throw new IOException("Expected integer for key '" + key + "'");
		}
		if (numericValue < Integer.MIN_VALUE || numericValue > Integer.MAX_VALUE) {
			throw new IOException("Integer value out of range for key '" + key + "'");
		}
		return value.intValue();
	}

	private static void validateNonNegative(int value, String key) throws IOException {
		if (value < 0) {
			throw new IOException("Expected non-negative integer for key '" + key + "'");
		}
	}

	private static void validateOptionalNonNegative(Integer value, String key) throws IOException {
		if (value != null && value < 0) {
			throw new IOException("Expected non-negative integer or null for key '" + key + "'");
		}
	}

	private static void validatePhaseState(GamePhase phase, Integer nominatedSeat, Integer markedSeat) throws IOException {
		if (nominatedSeat != null
				&& phase != GamePhase.NOMINATION
				&& phase != GamePhase.VOTING
				&& phase != GamePhase.EXECUTION) {
			throw new IOException("nominatedSeat is not valid during phase " + phase);
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> requireObject(Object value, String key) throws IOException {
		if (value instanceof Map<?, ?> map) {
			return (Map<String, Object>) map;
		}
		throw new IOException("Expected object for " + key);
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
		private int depth;

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
			enterComposite();
			try {
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
				if (map.containsKey(key)) {
					throw new IOException("Duplicate key in object: " + key);
				}
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
			} finally {
				exitComposite();
			}
		}

		List<Object> readArray() throws IOException {
			enterComposite();
			try {
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
			} finally {
				exitComposite();
			}
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
						case 'b' -> sb.append('\b');
						case 'f' -> sb.append('\f');
						case 'n' -> sb.append('\n');
						case 't' -> sb.append('\t');
						case 'r' -> sb.append('\r');
						case '/' -> sb.append('/');
						case 'u' -> {
							char unicodeChar = readUnicodeEscapeChar();
							if (Character.isHighSurrogate(unicodeChar)) {
								if (pos + 6 > json.length() || json.charAt(pos) != '\\' || json.charAt(pos + 1) != 'u') {
									throw new IOException("Unpaired high surrogate escape");
								}
								pos += 2;
								char lowSurrogate = readUnicodeEscapeChar();
								if (!Character.isLowSurrogate(lowSurrogate)) {
									throw new IOException("Expected low surrogate escape after high surrogate");
								}
								sb.append(unicodeChar).append(lowSurrogate);
							} else if (Character.isLowSurrogate(unicodeChar)) {
								throw new IOException("Unpaired low surrogate escape");
							} else {
								sb.append(unicodeChar);
							}
						}
						default -> throw new IOException("Unknown escape sequence: \\" + escaped);
					}
				} else {
					if (c < 0x20) {
						throw new IOException("Unescaped control character in string at position " + (pos - 1));
					}
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

			if (pos >= json.length() || !Character.isDigit(json.charAt(pos))) {
				throw new IOException("Invalid numeric value at position " + start);
			}

			if (json.charAt(pos) == '0') {
				pos++;
				if (pos < json.length() && Character.isDigit(json.charAt(pos))) {
					throw new IOException("Invalid numeric value at position " + start + ": leading zero");
				}
			} else {
				while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
					pos++;
				}
			}
			if (pos < json.length() && json.charAt(pos) == '.') {
				pos++;
				if (pos >= json.length() || !Character.isDigit(json.charAt(pos))) {
					throw new IOException("Invalid numeric value at position " + start + ": missing digits after decimal point");
				}
				while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
					pos++;
				}
			}
			if (pos < json.length() && (json.charAt(pos) == 'e' || json.charAt(pos) == 'E')) {
				pos++;
				if (pos < json.length() && (json.charAt(pos) == '+' || json.charAt(pos) == '-')) {
					pos++;
				}
				if (pos >= json.length() || !Character.isDigit(json.charAt(pos))) {
					throw new IOException("Invalid numeric value at position " + start + ": missing exponent digits");
				}
				while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
					pos++;
				}
			}

			String numStr = json.substring(start, pos);
			try {
				if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
					return new BigDecimal(numStr);
				}
				try {
					return Integer.parseInt(numStr);
				} catch (NumberFormatException ignored) {
					return new BigDecimal(numStr);
				}
			} catch (NumberFormatException | ArithmeticException e) {
				throw new IOException("Invalid numeric value at position " + start + ": " + numStr, e);
			}
		}

		void ensureFullyConsumed() throws IOException {
			skipWhitespace();
			if (pos != json.length()) {
				throw new IOException("Unexpected trailing content at position " + pos);
			}
		}

		private char readUnicodeEscapeChar() throws IOException {
			if (pos + 4 > json.length()) {
				throw new IOException("Incomplete unicode escape");
			}
			String hex = json.substring(pos, pos + 4);
			pos += 4;
			try {
				return (char) Integer.parseInt(hex, 16);
			} catch (NumberFormatException e) {
				throw new IOException("Invalid unicode escape: \\u" + hex, e);
			}
		}

		private void enterComposite() throws IOException {
			if (depth >= MAX_JSON_NESTING_DEPTH) {
				throw new IOException("JSON nesting depth exceeds maximum of " + MAX_JSON_NESTING_DEPTH);
			}
			depth++;
		}

		private void exitComposite() {
			depth--;
		}
	}
}
