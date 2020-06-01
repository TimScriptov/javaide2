package com.duy.android.compiler.builder.task.java;

import androidx.annotation.NonNull;

import android.util.Log;

import com.duy.android.compiler.builder.IBuilder;
import com.duy.android.compiler.builder.task.Task;
import com.duy.android.compiler.builder.util.MD5Hash;
import com.duy.android.compiler.project.JavaProject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DexTask extends Task<JavaProject> {
    private static final String TAG = "Dexer";

    public DexTask(IBuilder<? extends JavaProject> builder) {
        super(builder);
    }

    @Override
    public String getTaskName() {
        return "Dx";
    }

    @Override
    public boolean doFullTaskAction() throws Exception {
        Log.d(TAG, "convertToDexFormat() called with: projectFile = [" + mProject + "]");

        mBuilder.stdout("Android dx");

        if (!dexLibsD8(mProject)) {
            return false;
        }

        if (!dexBuildClassesD8(mProject)) {
            return false;
        }
        return true;
    }

    private boolean dexLibsD8(@NonNull JavaProject project) throws Exception {
        mBuilder.stdout("Dex libs");
        ArrayList<File> javaLibraries = project.getJavaLibraries();
        for (File jarLib : javaLibraries) {
            String md5 = MD5Hash.getMD5Checksum(jarLib);
            File dexLib = new File(project.getDirBuildDexedLibs(), jarLib.getName().replace(".jar", "-" + md5));
            //File dexLib = new File(jarLib.getParentFile(), jarLib.getName().replace(".jar", ""));
            if(!dexLib.exists()){
                dexLib.mkdir();
            }

            String[] args = getLibArgs(dexLib.getAbsolutePath(), jarLib.getAbsolutePath(), mProject.getBootClassPath(context));

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
    private boolean dexBuildClassesD8(@NonNull JavaProject project) throws IOException {
        mBuilder.stdout("Merge build classes");
        File buildClasseDir = project.getDirBuildClasses();

        String[] args = getArgs(buildClasseDir.getAbsolutePath(), project.getDexFile().getAbsolutePath(), mProject.getBootClassPath(context));
        com.android.tools.r8.D8.main(args);
        mBuilder.stdout("Merged build classes " + project.getDexFile().getName());
        return true;
    }

    private String[] getArgs(String input, String output, String lib)  {
        ArrayList<String> alist = new ArrayList<>();
        File f = new File(output);

        String outPath = f.getParentFile().getAbsolutePath();
        alist.add("--output");
        alist.add(outPath);
        alist.add("--lib");
        alist.add(lib);

        List<String> cl = classes(input);
        List<String> libs = classes(mProject.getDirBuildDexedLibs().toString());
        alist.addAll(cl);
        alist.addAll(libs);

        return alist.toArray(new String[0]);
    }

    private static List<String> classes(String absolutePath) {
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
            } else {
                lst.add(f.getAbsolutePath());
            }
        }
    }

    /*private boolean dexMerge(@NonNull JavaProject projectFile) throws IOException {
        mBuilder.stdout("Merge dex files");
        File[] dexedLibs = getLibs(projectFile.getDirBuildDexedLibs());

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



    private File[] getLibs(File f) {
        ArrayList<File> files = new ArrayList<>();
        walk(f,files,".dex");
        return files.toArray(new File[0]);
    }

    private static void walk(File dir, List<File> lst, final String filter) {
        File root = new File(dir.getAbsolutePath());
        File[] list = root.listFiles(new FileFilter(){
            @Override
            public boolean accept(File file) {
                if(file.isDirectory()||file.getName().endsWith(filter))
                    return true;
                return false;
            }
        });

        if (list == null) return;

        for ( File f : list ) {
            if ( f.isDirectory() ) {
                walk( f, lst ,filter);
            }
            else {
                lst.add(f);
            }
        }
    }*/

    /*private boolean dexMerge(@NonNull JavaProject projectFile) throws IOException {
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
    }*/
}