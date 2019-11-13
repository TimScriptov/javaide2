package com.duy.android.compiler.project;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.android.annotations.NonNull;
import com.android.builder.dependency.LibraryBundle;
import com.android.builder.dependency.LibraryDependency;
import com.android.ide.common.xml.AndroidManifestParser;
import com.android.ide.common.xml.ManifestData;
import com.android.io.StreamException;
import com.duy.android.compiler.builder.internal.dependency.LibraryDependencyImpl;
import com.duy.android.compiler.env.Environment;
import com.duy.android.compiler.library.LibraryCache;
import com.duy.android.compiler.utils.FileUtils;
import com.duy.common.io.IOUtils;

import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

public class AndroidProjectManager implements IAndroidProjectManager {
    private static final String TAG = "AndroidProjectManager";
    private Context context;

    public AndroidProjectManager(Context context) {
        this.context = context;
    }

    /**
     * Create new android project
     *
     * @param context          - android context to get assets template
     * @param dir              - The directory will contain the project
     * @param projectName      - Name of project, it will be used for create root directory
     * @param useCompatLibrary - <code>true</code> if need copy android compat library
     */
    @Override
    public AndroidAppProject createNewProject(Context context, File dir, String projectName,
                                              String packageName, String activityName, String mainLayoutName,
                                              String appName, boolean useCompatLibrary) throws Exception {

        String activityClass = String.format("%s.%s", packageName, activityName);
        File projectDir = new File(dir, projectName);
        AndroidAppProject project = new AndroidAppProject(projectDir, activityClass, packageName);
        //create directory
        project.mkdirs();

        AssetManager assets = context.getAssets();
        createGradleFile(project);
        createRes(project, useCompatLibrary, appName);
        createManifest(project, activityClass, packageName, assets);
        createMainActivity(project, activityClass, packageName, activityName, mainLayoutName, useCompatLibrary, assets);
        createMainLayoutXml(project, mainLayoutName, activityName, assets);
        copyLibrary(project, useCompatLibrary);

        return project;
    }

    /**
     * Load previous project
     *
     * @param rootDir     - root dir
     * @param tryToImport -  if not found gradle file, try to create it instead of throw exception
     */
    @NonNull
    @Override
    public AndroidAppProject loadProject(File rootDir, boolean tryToImport) throws Exception {
        AndroidAppProject project = new AndroidAppProject(rootDir, null, null);
        File file = new File(rootDir, AndroidGradleFileGenerator.DEFAULT_SETTING_FILE);
        if (!file.exists()) {
            //old version
            file = new File(rootDir, "setting.gradle");
            if (!file.exists()) {
                if (!tryToImport) {
                    throw new IOException("Can not find settings.gradle, try to create new project");
                } else {
                    AndroidGradleFileGenerator generator = new AndroidGradleFileGenerator(context, project);
                    generator.generate();
                }
            } else {
                file.renameTo(new File(rootDir, AndroidGradleFileGenerator.DEFAULT_SETTING_FILE));
                file = new File(rootDir, AndroidGradleFileGenerator.DEFAULT_SETTING_FILE);
            }
        }

        //compatible with old version
        if (tryToImport) {
            File oldLibs = new File(project.getRootDir(), "libs");
            if (oldLibs.exists()) {
                FileUtils.copyDirectory(oldLibs, project.getDirLibs());
                FileUtils.deleteDirectory(oldLibs);
            }

            File oldJavaDir = new File(project.getRootDir(), "src/main/java");
            if (oldJavaDir.exists()) {
                FileUtils.copyDirectory(oldJavaDir, project.getJavaSrcDir());
                FileUtils.deleteDirectory(oldJavaDir);
            }
            File oldResDir = new File(project.getRootDir(), "src/main/res");
            if (oldResDir.exists()) {
                FileUtils.copyDirectory(oldResDir, project.getResDir());
                FileUtils.deleteDirectory(oldResDir);
            }

            File oldAssetsDir = new File(project.getRootDir(), "src/main/assets");
            if (oldAssetsDir.exists()) {
                FileUtils.copyDirectory(oldAssetsDir, project.getAssetsDir());
                FileUtils.deleteDirectory(oldAssetsDir);
            }

            File oldManifest = new File(project.getRootDir(), "src/main/AndroidManifest.xml");
            if (oldManifest.exists()) {
                FileUtils.copyFile(oldManifest, project.getManifestFile());
                FileUtils.deleteQuietly(oldManifest);
            }
        }


        // TODO: 03-Jun-18 parse groovy file
        String content = IOUtils.toString(new FileInputStream(file));
        Pattern pattern = Pattern.compile("(include\\s+')(.*)'");
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            return project;
        }
        /// TODO: 03-Jun-18 dynamic change it
        String appDir = matcher.group(2);
        //find AndroidManifest
        if (project.getManifestFile().exists()) {
            ManifestData manifestData = AndroidManifestParser.parse(new FileInputStream(project.getManifestFile()));
            ManifestData.Activity launcherActivity = manifestData.getLauncherActivity();
            if (launcherActivity != null) {
                project.setPackageName(manifestData.getPackage());
            }
            Log.d(TAG, "importAndroidProject launcherActivity = " + launcherActivity);
        } else {
            throw new IOException("Can not find AndroidManifest.xml");
        }
        return project;
    }

    private void createGradleFile(AndroidAppProject project) throws IOException {
        AndroidGradleFileGenerator generator = new AndroidGradleFileGenerator(context, project);
        generator.generate();
    }


    private void createRes(AndroidAppProject project, boolean useAppCompat, String appName) throws IOException {
        File resDir = project.getResDir();

        //mipmap
        copyAssets("templates/app/ic_launcher_hdpi.png",
                new File(resDir, "mipmap-hdpi/ic_launcher.png"));
        copyAssets("templates/app/ic_launcher_mdpi.png",
                new File(resDir, "mipmap-mdpi/ic_launcher.png"));
        copyAssets("templates/app/ic_launcher_xhdpi.png",
                new File(resDir, "mipmap-xhdpi/ic_launcher.png"));
        copyAssets("templates/app/ic_launcher_xxhdpi.png",
                new File(resDir, "mipmap-xxhdpi/ic_launcher.png"));
        copyAssets("templates/app/ic_launcher_xxxhdpi.png",
                new File(resDir, "mipmap-xxxhdpi/ic_launcher.png"));

        //mipmap round
        copyAssets("templates/app/ic_launcher_round_hdpi.png",
                new File(resDir, "mipmap-hdpi/ic_launcher_round.png"));
        copyAssets("templates/app/ic_launcher_round_mdpi.png",
                new File(resDir, "mipmap-mdpi/ic_launcher_round.png"));
        copyAssets("templates/app/ic_launcher_round_xhdpi.png",
                new File(resDir, "mipmap-xhdpi/ic_launcher_round.png"));
        copyAssets("templates/app/ic_launcher_round_xxhdpi.png",
                new File(resDir, "mipmap-xxhdpi/ic_launcher_round.png"));
        copyAssets("templates/app/ic_launcher_round_xxxhdpi.png",
                new File(resDir, "mipmap-xxxhdpi/ic_launcher_round.png"));

        //styles
        File style = new File(resDir, "values/styles.xml");
        String content = IOUtils.toString(
                context.getAssets().open("templates/app/styles.xml"), "UTF-8");
        content = content.replace("APP_STYLE", useAppCompat
                ? "Theme.AppCompat.Light.DarkActionBar" : "@android:style/Theme.Light");
        saveFile(style, content);

        File string = new File(resDir, "values/strings.xml");
        content = IOUtils.toString(
                context.getAssets().open("templates/app/strings.xml"), "UTF-8");
        content = content.replace("APP_NAME", appName);
        content = content.replace("MAIN_ACTIVITY_NAME", appName);
        saveFile(string, content);
    }

    private void createManifest(AndroidAppProject project, String activityClass, String packageName,
                                AssetManager assets) throws IOException {
        File manifest = project.getManifestFile();
        String content = IOUtils.toString(assets.open("templates/app/AndroidManifest.xml"));

        content = content.replace("PACKAGE", packageName);
        content = content.replace("MAIN_ACTIVITY", activityClass);
        saveFile(manifest, content);
    }

    private void createMainActivity(AndroidAppProject project, String activityClass,
                                    String packageName, String activityName, String layoutName,
                                    boolean useAppCompat, AssetManager assets) throws IOException {
        File activityFile = new File(project.getJavaSrcDir(),
                activityClass.replace(".", File.separator) + ".java");

        String name = useAppCompat ? "templates/app/MainActivityAppCompat.java" : "templates/app/MainActivity.java";
        String content = IOUtils.toString(assets.open(name));
        content = content.replace("PACKAGE", packageName);
        content = content.replace("ACTIVITY_NAME", activityName);
        content = content.replace("ACTIVITY_MAIN", layoutName);
        saveFile(activityFile, content);
    }

    private void createMainLayoutXml(AndroidAppProject project, String layoutName, String activityName,
                                AssetManager assets) throws IOException {
        File layoutMain = new File(project.getResDir(), "layout/" + layoutName);
        String content = IOUtils.toString(assets.open("templates/app/activity_main.xml"));

        content = content.replace("ACTIVITY_NAME", activityName);
        saveFile(layoutMain, content);
    }

    private void copyAssets(String assetsPath, File outFile) throws IOException {
        outFile.getParentFile().mkdirs();
        FileOutputStream output = new FileOutputStream(outFile);
        InputStream input = context.getAssets().open(assetsPath);
        IOUtils.copy(input, output);
        input.close();
        output.close();
    }

    private void saveFile(File file, String content) throws IOException {
        file.getParentFile().mkdirs();
        FileOutputStream output = new FileOutputStream(file);
        IOUtils.write(content, output);
        output.close();
    }

    private static final  String path = "libs/com/android/support/";
    private static final  String path1 = "libs/android/arch/core/";
    private static final  String path2 = "libs/android/arch/lifecycle/";

    private static final  String pathx = "libs/androidx/";

    private void copyLibrary(AndroidAppProject project, boolean useCompatLibrary)
            throws IOException, StreamException, SAXException, ParserConfigurationException {
        if (useCompatLibrary) {
            // androidx
            addLib(project, pathx + "activity/1.0.0/activity-1.0.0.aar", pathx + "activity/1.0.0/activity-1.0.0");
            addLib(project, pathx + "appcompat/1.1.0/appcompat-1.1.0.aar", pathx + "appcompat/1.1.0/appcompat-1.1.0");
            addLib(project, pathx + "appcompat-resources/1.1.0/appcompat-resources-1.1.0.aar", pathx + "appcompat-resources/1.1.0/appcompat-resources-1.1.0");
            addLib(project, pathx + "constraintlayout/1.1.3/constraintlayout-1.1.3.aar", pathx + "constraintlayout/1.1.3/constraintlayout-1.1.3");
            addLib(project, pathx + "core/1.1.0/core-1.1.0.aar", pathx + "core/1.1.0/core-1.1.0");
            addLib(project, pathx + "core-runtime/2.0.0/core-runtime-2.0.0.aar", pathx + "core-runtime/2.0.0/core-runtime-2.0.0");
            addLib(project, pathx + "cursoradapter/1.0.0/cursoradapter-1.0.0.aar", pathx + "cursoradapter/1.0.0/cursoradapter-1.0.0");
            addLib(project, pathx + "customview/1.0.0/customview-1.0.0.aar", pathx + "customview/1.0.0/customview-1.0.0");
            addLib(project, pathx + "drawerlayout/1.0.0/drawerlayout-1.0.0.aar", pathx + "drawerlayout/1.0.0/drawerlayout-1.0.0");
            addLib(project, pathx + "fragment/1.1.0/fragment-1.1.0.aar", pathx + "fragment/1.1.0/fragment-1.1.0");
            addLib(project, pathx + "interpolator/1.0.0/interpolator-1.0.0.aar", pathx + "interpolator/1.0.0/interpolator-1.0.0");
            addLib(project, pathx + "lifecycle-livedata/2.0.0/lifecycle-livedata-2.0.0.aar", pathx + "lifecycle-livedata/2.0.0/lifecycle-livedata-2.0.0");
            addLib(project, pathx + "lifecycle-livedata-core/2.0.0/lifecycle-livedata-core-2.0.0.aar", pathx + "lifecycle-livedata-core/2.0.0/lifecycle-livedata-core-2.0.0");
            addLib(project, pathx + "lifecycle-runtime/2.1.0/lifecycle-runtime-2.1.0.aar", pathx + "lifecycle-runtime/2.1.0/lifecycle-runtime-2.1.0");
            addLib(project, pathx + "lifecycle-viewmodel/2.1.0/lifecycle-viewmodel-2.1.0.aar", pathx + "lifecycle-viewmodel/2.1.0/lifecycle-viewmodel-2.1.0");
            addLib(project, pathx + "loader/1.0.0/loader-1.0.0.aar", pathx + "loader/1.0.0/loader-1.0.0");
            addLib(project, pathx + "savedstate/1.0.0/savedstate-1.0.0.aar", pathx + "savedstate/1.0.0/savedstate-1.0.0");
            addLib(project, pathx + "vectordrawable/1.1.0/vectordrawable-1.1.0.aar", pathx + "vectordrawable/1.1.0/vectordrawable-1.1.0");
            addLib(project, pathx + "vectordrawable-animated/1.1.0/vectordrawable-animated-1.1.0.aar", pathx + "vectordrawable-animated/1.1.0/vectordrawable-animated-1.1.0");
            addLib(project, pathx + "versionedparcelable/1.1.0/versionedparcelable-1.1.0.aar", pathx + "versionedparcelable/1.1.0/versionedparcelable-1.1.0");
            addLib(project, pathx + "viewpager/1.0.0/viewpager-1.0.0.aar", pathx + "viewpager/1.0.0/viewpager-1.0.0");

            /*
            addLib(project, path + "animated-vector-drawable/27.1.1/animated-vector-drawable-27.1.1.aar", "com/android/support/animated-vector-drawable-27.1.1");
            addLib(project, path + "appcompat-v7/27.1.1/appcompat-v7-27.1.1.aar", "com/android/support/appcompat-v7-27.1.1");
            addLib(project, path + "cardview-v7/27.1.1/cardview-v7-27.1.1.aar", "com/android/support/cardview-v7-27.1.1");
            addLib(project, path + "design/27.1.1/design-27.1.1.aar", "com/android/support/design-27.1.1");
            addLib(project, path + "recyclerview-v7/27.1.1/recyclerview-v7-27.1.1.aar", "com/android/support/recyclerview-v7-27.1.1");
            //addLib(project, path + "support-annotations/27.1.1/support-annotations-27.1.1.jar", "com/android/support/support-annotations-27.1.1.jar");
            addLib(project, path + "support-compat/27.1.1/support-compat-27.1.1.aar", "com/android/support/support-compat-27.1.1");
            addLib(project, path + "support-core-ui/27.1.1/support-core-ui-27.1.1.aar", "com/android/support/support-core-ui-27.1.1");
            addLib(project, path + "support-core-utils/27.1.1/support-core-utils-27.1.1.aar", "com/android/support/support-core-utils-27.1.1");
            addLib(project, path + "support-fragment/27.1.1/support-fragment-27.1.1.aar", "com/android/support/support-fragment-27.1.1");
            addLib(project, path + "support-media-compat/27.1.1/support-media-compat-27.1.1.aar", "com/android/support/support-media-compat-27.1.1");
            addLib(project, path + "support-v4/27.1.1/support-v4-27.1.1.aar", "com/android/support/support-v4-27.1.1");
            addLib(project, path + "support-vector-drawable/27.1.1/support-vector-drawable-27.1.1.aar", "com/android/support/support-vector-drawable-27.1.1");
            addLib(project, path + "transition/27.1.1/transition-27.1.1.aar", "com/android/support/transition-27.1.1");
            //addLib(project, path1 + "common/1.1.1/common-1.1.1.jar", "android/arch/core/common-1.1.1.jar");
            addLib(project, path1 + "runtime/1.1.1/runtime-1.1.1.aar", "android/arch/core/runtime/1.1.1/runtime-1.1.1");
            //addLib(project, path2 + "common/1.1.1/common-1.1.1.jar", "android/arch/lifecycle/common-1.1.1.jar");
            addLib(project, path2 + "extensions/1.1.1/extensions-1.1.1.aar", "android/arch/lifecycle/extensions/1.1.1/extensions-1.1.1");
            addLib(project, path2 + "livedata/1.1.1/livedata-1.1.1.aar", "android/arch/lifecycle/livedata/1.1.1/livedata-1.1.1");
            addLib(project, path2 + "livedata-core/1.1.1/livedata-core-1.1.1.aar", "android/arch/lifecycle/livedata-core/1.1.1/livedata-core-1.1.1");
            addLib(project, path2 + "runtime/1.1.1/runtime-1.1.1.aar", "android/arch/lifecycle/runtime/1.1.1/runtime-1.1.1");
            addLib(project, path2 + "viewmodel/1.1.1/viewmodel-1.1.1.aar", "android/arch/lifecycle/viewmodel/1.1.1/viewmodel-1.1.1");
            */
        }
    }

    private void addLib(AndroidAppProject project, String assetsPath, String bundleFolderName)
            throws SAXException, StreamException, ParserConfigurationException, IOException {
        if (assetsPath.endsWith(".jar")) {
            File javaLib = new File(project.getDirLibs(), bundleFolderName);
            FileOutputStream output = new FileOutputStream(javaLib);
            IOUtils.copy(context.getAssets().open(assetsPath), output);
            output.close();

        } else if (assetsPath.endsWith(".aar")) {
            File libraryExtractedFolder = Environment.getSdCardLibraryExtractedFolder();
            File libraryBundleFolder = Environment.getSdCardLibraryBundleFolder();

            String bundleName = assetsPath.substring(assetsPath.lastIndexOf("/"));
            File bundle = new File(libraryBundleFolder, bundleName);
            bundle.getParentFile().mkdirs();
            FileOutputStream output = new FileOutputStream(bundle);
            IOUtils.copy(context.getAssets().open(assetsPath), output);
            output.close();

            LibraryCache extractor = new LibraryCache(context);
            File folderOut = new File(libraryExtractedFolder, bundleFolderName);
            extractor.extractAar(bundle, folderOut);

            LibraryBundle androidLib = new LibraryDependencyImpl(bundle, folderOut, new ArrayList<LibraryDependency>(),
                    bundleFolderName, null, project.getRootDir().getAbsolutePath(), null, null, false);
            project.addLibrary(androidLib);
        }
    }
}