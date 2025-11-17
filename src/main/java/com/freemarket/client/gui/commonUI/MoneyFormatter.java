package com.freemarket.client.gui.commonUI;

/**
 * Utility class for formatting money values with k/m/b/t suffixes.
 * Allows up to 3 decimal places when there are trailing values.
 * Examples: 1000 -> 1k, 1036 -> 1.036k, 10360 -> 10.36k, 10361 -> 10361 (needs 4 decimals)
 */
public class MoneyFormatter {

    /**
     * Formats a number with k/m/b/t suffixes, allowing up to 3 decimal places.
     * 
     * @param value The money value to format
     * @return Formatted string with suffix (e.g., "1.036k", "10m", "10361")
     */
    public static String formatWithSuffix(long value) {
        if (value < 1000) {
            return String.valueOf(value);
        }

        String valueStr = String.valueOf(value);

        // Format with suffix, allowing up to 3 decimal places
        if (value >= 1_000_000_000_000L) {
            long divisor = 1_000_000_000_000L;
            double result = value / (double) divisor;
            if (result >= 1.0) {
                String formatted = formatWithDecimals(result, "t");
                if (formatted != null) return formatted;
            }
        }
        if (value >= 1_000_000_000L) {
            long divisor = 1_000_000_000L;
            double result = value / (double) divisor;
            if (result >= 1.0) {
                String formatted = formatWithDecimals(result, "b");
                if (formatted != null) return formatted;
            }
        }
        if (value >= 1_000_000L) {
            long divisor = 1_000_000L;
            double result = value / (double) divisor;
            if (result >= 1.0) {
                String formatted = formatWithDecimals(result, "m");
                if (formatted != null) return formatted;
            }
        }
        if (value >= 1_000L) {
            long divisor = 1_000L;
            double result = value / (double) divisor;
            if (result >= 1.0) {
                String formatted = formatWithDecimals(result, "k");
                if (formatted != null) return formatted;
            }
        }

        return valueStr;
    }

    /**
     * Formats a number with suffix, allowing up to 3 decimal places.
     * Returns null if more than 3 decimal places would be needed.
     * 
     * @param value The decimal value to format
     * @param suffix The suffix to append (k, m, b, t)
     * @return Formatted string with suffix, or null if more than 3 decimals needed
     */
    private static String formatWithDecimals(double value, String suffix) {
        // Check if it's a whole number
        long wholePart = (long) value;
        if (value == wholePart) {
            return wholePart + suffix;
        }

        // Calculate decimal part by working with the original value
        // We need to determine how many significant decimal places are needed
        // Multiply by 1000 to get 3 decimal places of precision
        long scaled = Math.round(value * 1000);
        long wholeScaled = wholePart * 1000;
        long remainder = scaled - wholeScaled;
        
        // If remainder is 0, it's a whole number
        if (remainder == 0) {
            return wholePart + suffix;
        }
        
        // Count trailing zeros in remainder
        int trailingZeros = 0;
        long temp = remainder;
        while (temp % 10 == 0 && temp > 0) {
            trailingZeros++;
            temp /= 10;
        }
        
        // Calculate significant digits (up to 3)
        int significantDigits = 3 - trailingZeros;
        
        // If we need more than 3 decimal places, return null
        if (significantDigits > 3) {
            return null;
        }
        
        // Format with the appropriate number of decimal places
        if (significantDigits == 0) {
            return wholePart + suffix;
        }
        
        // Calculate the actual decimal value
        double decimalValue = remainder / 1000.0;
        String formatted = String.format("%." + significantDigits + "f", wholePart + decimalValue);
        // Remove any trailing zeros that might have been added
        formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        return formatted + suffix;
    }
}

