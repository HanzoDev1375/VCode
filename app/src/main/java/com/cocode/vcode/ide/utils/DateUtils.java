package com.cocode.vcode.ide.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Universal date formatting utility class.
 * Computes human-readable relative time expressions and absolute text timestamps
 * for commit logs and file histories.
 */
public class DateUtils {

    // Prevent direct instantiation since all methods are utility functions
    private DateUtils() {
    }

    /**
     * Converts absolute date values into relative textual representations.
     *
     * @param date The baseline target timestamp value.
     * @return A localized narrative segment description representing the elapsed interval.
     */
    public static String getRelativeTime(Date date) {
        if (date == null) return "unknown";
        long now = System.currentTimeMillis();
        long diff = now - date.getTime();

        // Account for slight internal processing time alignment variations
        if (diff < 0) return "just now";

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long weeks = days / 7;
        long months = days / 30;
        long years = days / 365;

        if (seconds < 60) return "just now";
        if (minutes < 60) return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
        if (days < 7) return days + (days == 1 ? " day ago" : " days ago");
        if (weeks < 5) return weeks + (weeks == 1 ? " week ago" : " weeks ago");
        if (months < 12) return months + (months == 1 ? " month ago" : " months ago");
        return years + (years == 1 ? " year ago" : " years ago");
    }

    /**
     * Formats absolute calendar markers into full combined chronological descriptions.
     */
    public static String formatDate(Date date) {
        if (date == null) return "";
        return new SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(date);
    }

    /**
     * Formats date coordinates into streamlined single card day layout tracks.
     */
    public static String formatDateShort(Date date) {
        if (date == null) return "";
        return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date);
    }
}