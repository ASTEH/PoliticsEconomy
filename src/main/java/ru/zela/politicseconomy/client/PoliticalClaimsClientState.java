package ru.zela.politicseconomy.client;

import ru.zela.politicseconomy.network.PoliticalClaimsPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/** Client cache of discovered country-owned chunks for the political map. */
public final class PoliticalClaimsClientState {
    private static final Map<Long, String> CLAIMS = new HashMap<>();
    private static final List<EventMarker> EVENTS = new ArrayList<>();

    private PoliticalClaimsClientState() {}

    public static synchronized void apply(PoliticalClaimsPayload payload) {
        CLAIMS.clear();

        String[] countries = payload.countries();
        long[] chunks = payload.chunks();
        int[] indices = payload.countryIndices();
        int count = Math.min(chunks.length, indices.length);

        for (int i = 0; i < count; i++) {
            int index = indices[i];
            if (index < 0 || index >= countries.length) continue;
            CLAIMS.put(chunks[i], countries[index]);
        }

        EVENTS.clear();
        for (String row : payload.eventRows()) {
            String[] parts = row.split("\\|", -1);
            if (parts.length < 5) continue;
            try {
                int x = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                long expires = Long.parseLong(parts[4]);
                EVENTS.add(new EventMarker(parts[0], parts[1], x, z, expires));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public static synchronized Map<Long, String> snapshot() {
        return Map.copyOf(CLAIMS);
    }

    public static synchronized List<EventMarker> eventSnapshot() {
        return List.copyOf(EVENTS);
    }

    public static synchronized void clear() {
        CLAIMS.clear();
        EVENTS.clear();
    }

    public static int chunkX(long packed) {
        return (int) packed;
    }

    public static int chunkZ(long packed) {
        return (int) (packed >> 32);
    }

    public record EventMarker(String type, String stateName, int x, int z, long expiresAt) {}
}
