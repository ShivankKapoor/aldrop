package com.shivankkapoor.aldrop.Cache;

public record DataCacheStats(long hits, long misses, double hitRate, long size) {

    public static DataCacheStats of(long hits, long misses, long size) {
        long requests = hits + misses;
        double hitRate = requests == 0 ? 0.0 : (double) hits / requests;
        return new DataCacheStats(hits, misses, hitRate, size);
    }
}
