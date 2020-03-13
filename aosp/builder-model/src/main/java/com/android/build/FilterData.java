package com.android.build;
import com.android.annotations.NonNull;
/**
 * Represents a split information, like its type or dimension (density, abi, language...) and
 * the filter value (like hdpi for a density split type).
 */
public interface FilterData {
    @NonNull
    String getIdentifier();
    @NonNull
    String getFilterType();
    public static class Builder {
        public static FilterData build(final String filterType, final String identifier) {
            return new FilterDataImpl(filterType, identifier);
        }
    }
}