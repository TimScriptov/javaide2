package com.duy.android.compiler.builder.task.java;

import androidx.annotation.NonNull;

import android.preference.PreferenceManager;
import android.util.Log;

import com.android.dx.command.dexer.DxContext;
import com.duy.android.compiler.builder.IBuilder;
import com.duy.android.compiler.builder.task.Task;
import com.duy.android.compiler.builder.util.MD5Hash;
import com.duy.android.compiler.project.JavaProject;
import com.android.dex.Dex;
import com.android.dx.merge.CollisionPolicy;
import com.android.dx.merge.DexMerger;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DexTask extends Task<JavaProject> {
    private static final String TAG = "Dexer";

    public DexTask(IBuilder<? extends JavaProject> builder) {
        super(builder);
    }

    private boolean prefD8() {
        PreferenceManager.getDefaultSharedPreferences(context);
        return context.getSharedPreferences("com.duy.compiler.javanide_preferences", 0).getBoolean("key_pref_dex_compiler_select", false);
    }

    @Override
    public String getTaskName() {
        return "Dx";
    }

    @Override
    public boolean doFullTaskAction() throws Exception {
        Log.d(TAG, "convertToDexFormat() called with: projectFile = [" + mProject + "]");

        mBuilder.stdout("Android dx");

        if (prefD8() ? !dexLibsD8(mProject) : !dexLibs(mProject)) {
            return false;
        }

        if (prefD8() ? !dexBuildClassesD8(mProject) : !dexBuildClasses(mProject)) {
            return false;
        }

        if (!dexMerge(mProject)) {
            return false;
        }
        return true;
    }

    private boolean dexLibs(@NonNull JavaProject project) throws Exception {
        mBuilder.stdout("Dex libs");
        ArrayList<File> javaLibraries = project.getJavaLibraries();
        for (File jarLib : javaLibraries) {
            // compare hash of jar contents to name of dexed version
            String md5 = MD5Hash.getMD5Checksum(jarLib);

            File dexLib = new File(project.getDirBuildDexedLibs(), jarLib.getName().replace(".jar", "-" + md5 + ".dex"));
            if (dexLib.exists()) {
                mBuilder.stdout("Lib " + jarLib.getPath() + " has been dexed with cached file " + dexLib.getName());
                continue;
            }

            String[] args = {"--verbose",
                    "--no-strict",
                    "--no-files",
                    "--output=" + dexLib.getAbsolutePath(), //output
                    jarLib.getAbsolutePath() //input
            };
            mBuilder.stdout("Dexing lib " + jarLib.getPath() + " => " + dexLib.getAbsolutePath());
            com.android.dx.command.dexer.Main.main(args);
            mBuilder.stdout("Dexed lib " + dexLib.getAbsolutePath());
        }
        mBuilder.stdout("Dex libs completed");
        return true;
    }

    private boolean dexLibsD8(@NonNull JavaProject project) throws Exception {
        mBuilder.stdout("Dex libs");
        ArrayList<File> javaLibraries = project.getJavaLibraries();
        for (File jarLib : javaLibraries) {

            File dexLib = new File(jarLib.getParentFile(), jarLib.getName().replace(".jar", ""));
            if(!dexLib.exists()){
                dexLib.mkdir();
            }

            String[] args = getLibArgs(dexLib.getAbsolutePath(), jarLib.getAbsolutePath(), mBuilder.getBootClassPath());

            mBuilder.stdout("Dexing lib " + jarLib.getPath() + " => " + dexLib.getAbsolutePath());
            com.android.tools.r8.D8.main(args);
            mBuilder.stdout("Dexed lib " + dexLib.getAbsolutePath());
        }
        mBuilder.stdout("Dex libs completed");
        return true;
    }

    private static String[] getLibArgs(String dex, String jar, String lib) {
        ArrayList<String> alist = new ArrayList<>();
        alist.add("--output");
        alist.add(dex);
        alist.add("--lib");
        alist.add(lib);
        alist.add(jar);

        return alist.toArray(new String[0]);
    }

    /**
     * Merge all classed has been build by {@link CompileJavaTask} to a single file .dex
     */
    private boolean dexBuildClasses(@NonNull JavaProject project) throws IOException {
        mBuilder.stdout("Merge build classes");

        File buildClasseDir = project.getDirBuildClasses();
        String[] args = new String[]{
                "--verbose", "--no-strict",
                "--output=" + project.getDexFile().getAbsolutePath(), //output dex file
                buildClasseDir.getAbsolutePath() //input files
        };
        com.android.dx.command.dexer.Main.main(args);
        mBuilder.stdout("Merged build classes " + project.getDexFile().getName());
        return true;
    }

    private boolean dexBuildClassesD8(@NonNull JavaProject project) throws IOException {
        mBuilder.stdout("Merge build classes");

        File buildClasseDir = project.getDirBuildClasses();
        String[] args = getArgs(buildClasseDir.getAbsolutePath() ,project.getDexFile().getAbsolutePath(), mBuilder.getBootClassPath());
        com.android.tools.r8.D8.main(args);
        mBuilder.stdout("Merged build classes " + project.getDexFile().getName());
        return true;
    }

    private static String[] getArgs(String input, String output, String lib)
    {
        ArrayList<String> alist = new ArrayList<String>();
        File f = new File(output);
        String outPath = f.getParentFile().getAbsolutePath();
        alist.add("--output");
        alist.add(outPath);
        alist.add("--lib");
        alist.add(lib);

        List<String> cl = classes(input);

        alist.addAll(cl);

        return alist.toArray(new String[0]);
    }

    private static List<String> classes(String absolutePath)
    {
        ArrayList<String> list = new ArrayList<String>();
        walk(absolutePath, list);

        return list;
    }

    private static void walk( String path, List<String> lst ) {

        File root = new File( path );
        File[] list = root.listFiles();

        if (list == null) return;

        for ( File f : list ) {
            if ( f.isDirectory() ) {
                walk( f.getAbsolutePath(), lst );

            }
            else {
                lst.add(f.getAbsolutePath());
            }
        }
    }

    private boolean dexMerge(@NonNull JavaProject projectFile) throws IOException {
        mBuilder.stdout("Merge dex files");
        File[] dexedLibs = projectFile.getDirBuildDexedLibs().listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isFile() && pathname.getName().endsWith(".dex");
            }
        });
        if (dexedLibs.length >= 1) {
            for (File dexedLib : dexedLibs) {
                Dex[] toBeMerge = {new Dex(projectFile.getDexFile()), new Dex(dexedLib)};
                DexMerger dexMerger = new DexMerger(toBeMerge, CollisionPolicy.FAIL, new DxContext());
                Dex merged = dexMerger.merge();
                merged.writeTo(projectFile.getDexFile());
            }
        }
        mBuilder.stdout("Merge all dexed files completed");
        return true;
    }
}