package dev.kgoodwin.midnightcouncil.api.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GameEventDispatcher {

	private final Map<Class<?>, List<Consumer<?>>> handlers = new ConcurrentHashMap<>();

	public <T extends GameEvent> void registerListener(Class<T> eventClass, Consumer<T> handler) {
		handlers.computeIfAbsent(eventClass, key -> new CopyOnWriteArrayList<>()).add(handler);
	}

	@SuppressWarnings("unchecked")
	public void dispatch(GameEvent event) {
		List<Consumer<?>> eventHandlers = handlers.get(event.getClass());
		if (eventHandlers == null) {
			return;
		}

		for (Consumer<?> handler : eventHandlers) {
			try {
				((Consumer<GameEvent>) handler).accept(event);
			} catch (Exception e) {
				// Intentionally isolate failures so remaining handlers still execute.
			}
		}
	}
}
