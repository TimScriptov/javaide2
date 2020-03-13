package com.android.build;
import com.android.annotations.NonNull;
import java.io.File;
import java.util.Collection;
/**
 * basic variant output information
 * TODO(jedo): reconcile the numerous subclasses.
 */
public interface VariantOutput {
    /**
     * Returns the main file for this artifact which can be either the
     * {@link com.android.build.OutputFile.OutputType#MAIN} or
     * {@link com.android.build.OutputFile.OutputType#FULL_SPLIT}
     */
    @NonNull
    OutputFile getMainOutputFile();
    /**
     * All the output files for this artifacts, contains the main APK and optionally a list of
     * split APKs.
     */
    @NonNull
    Collection<? extends OutputFile> getOutputs();
    /**
     * Returns the folder containing all the split APK files.
     */
    @NonNull
    File getSplitFolder();
    /**
     * Returns the version code for this output.
     *
     * This is convenient method that returns the final version code whether it's coming
     * from the override set in the output or from the variant's merged flavor.
     *
     * @return the version code.
     */
    int getVersionCode();
}