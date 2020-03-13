package com.android.build;
import com.android.annotations.NonNull;
import java.io.Serializable;
/**
 * Implementation of {@link FilterData} interface
 */
class FilterDataImpl implements FilterData, Serializable {
    private final String filterType;
    private final String identifier;
    FilterDataImpl(String filterType, String identifier) {
        this.filterType = filterType;
        this.identifier = identifier;
    }
    @NonNull
    @Override
    public String getIdentifier() {
        return identifier;
    }
    @NonNull
    @Override
    public String getFilterType() {
        return filterType;
    }
}