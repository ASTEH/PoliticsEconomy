package ru.zela.politicseconomy.client;

import ru.zela.politicseconomy.network.PoliticalClaimsPayload;

import java.util.HashMap;
import java.util.Map;

/** Client cache of discovered country-owned chunks for the political map. */
public final class PoliticalClaimsClientState {
    private static final Map<Long, String> CLAIMS = new HashMap<>();

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
    }

    public static synchronized Map<Long, String> snapshot() {
        return Map.copyOf(CLAIMS);
    }

    public static synchronized void clear() {
        CLAIMS.clear();
    }

    public static int chunkX(long packed) {
        return (int) packed;
    }

    public static int chunkZ(long packed) {
        return (int) (packed >> 32);
    }
}
