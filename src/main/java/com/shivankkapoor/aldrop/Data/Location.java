package com.shivankkapoor.aldrop.Data;

public record Location(String city, String country) {
    public static final Location NONE = new Location(null, null);
}
