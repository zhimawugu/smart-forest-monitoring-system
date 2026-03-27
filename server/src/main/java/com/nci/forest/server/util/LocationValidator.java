package com.nci.forest.server.util;

/**
 * Geographic coordinate validation utility
 * Validates latitude and longitude according to WGS84 standard
 */
public class LocationValidator {
    
    private static final double LATITUDE_MIN = -90.0;
    private static final double LATITUDE_MAX = 90.0;
    private static final double LONGITUDE_MIN = -180.0;
    private static final double LONGITUDE_MAX = 180.0;
    
    /**
     * Validate latitude and longitude values
     * @return null if valid, error message if invalid
     */
    public static String validateCoordinates(double latitude, double longitude) {
        // Check for NaN or Infinity
        if (Double.isNaN(latitude) || Double.isNaN(longitude) || 
            Double.isInfinite(latitude) || Double.isInfinite(longitude)) {
            return "Invalid coordinates: NaN or Infinity detected";
        }
        
        // Check latitude range
        if (latitude < LATITUDE_MIN || latitude > LATITUDE_MAX) {
            return String.format("Invalid latitude: %.6f (must be between %.2f and %.2f)",
                latitude, LATITUDE_MIN, LATITUDE_MAX);
        }
        
        // Check longitude range
        if (longitude < LONGITUDE_MIN || longitude > LONGITUDE_MAX) {
            return String.format("Invalid longitude: %.6f (must be between %.2f and %.2f)",
                longitude, LONGITUDE_MIN, LONGITUDE_MAX);
        }
        
        return null; // Valid
    }
}





