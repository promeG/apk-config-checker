/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.promeg.configchecker;


import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class Main {

    String mAndroidJar;
    String mFlavor;
    String mBuildType;

    public static void main(String[] args) {
        Main main = new Main();
        main.run(args);
    }

    void run(String[] args) {

        try {
            String[] inputFileNames = parseArgs(args);
            for (String fileName : collectFileNames(inputFileNames)) {
                System.out.println("Processing " + fileName);
                List<String> dexFiles = openInputFiles(fileName);
                new DexChecker(mAndroidJar, dexFiles, mFlavor, mBuildType).run();
            }
        } catch (ConfigCheckFailException exp) {
            exp.printStackTrace();
            System.exit(3);
        } catch (UsageException ue) {
            usage();
            System.exit(2);
        } catch (IOException ioe) {
            if (ioe.getMessage() != null) {
                System.err.println("Failed: " + ioe);
            }
            System.exit(1);
        }
    }

    /**
     * Opens an input file, which could be a .dex or a .jar/.apk with a
     * classes.dex inside.  If the latter, we extract the contents to a
     * temporary file.
     */
    List<String> openInputFiles(String fileName) throws IOException {
        List<String> dexFiles = new ArrayList<String>();

        openInputFileAsZip(fileName, dexFiles);
        if (dexFiles.size() == 0) {
            File inputFile = new File(fileName);
            dexFiles.add(inputFile.getAbsolutePath());
        }

        return dexFiles;
    }

    /**
     * Tries to open an input file as a Zip archive (jar/apk) with a
     * "classes.dex" inside.
     */
    void openInputFileAsZip(String fileName, List<String> dexFiles) throws IOException {
        ZipFile zipFile;

        // Try it as a zip file.
        try {
            zipFile = new ZipFile(fileName);
        } catch (FileNotFoundException fnfe) {
            // not found, no point in retrying as non-zip.
            System.err.println("Unable to open '" + fileName + "': " +
                    fnfe.getMessage());
            throw fnfe;
        } catch (ZipException ze) {
            // not a zip
            return;
        }

        // Open and add all files matching "classes.*\.dex" in the zip file.
        for (ZipEntry entry : Collections.list(zipFile.entries())) {
            if (entry.getName().matches("classes.*\\.dex")) {
                dexFiles.add(openDexFile(zipFile, entry).getAbsolutePath());
            }
        }

        zipFile.close();
    }

    File openDexFile(ZipFile zipFile, ZipEntry entry) throws IOException  {
        // We know it's a zip; see if there's anything useful inside.  A
        // failure here results in some type of IOException (of which
        // ZipException is a subclass).
        InputStream zis = zipFile.getInputStream(entry);

        // Create a temp file to hold the DEX data, open it, and delete it
        // to ensure it doesn't hang around if we fail.
        File dexFile = File.createTempFile("dexdeps", ".dex");

        // Copy all data from input stream to output file.
        IOUtils.copy(zis, new FileOutputStream(dexFile));

        return dexFile;
    }

    private String[] parseArgs(String[] args) {
        int idx;

        for (idx = 0; idx < args.length; idx++) {
            String arg = args[idx];

            if (arg.equals("--") || !arg.startsWith("--")) {
                break;
            } else if (arg.startsWith("--androidjar=")) {
                mAndroidJar = arg.substring(arg.indexOf('=') + 1);
            } else if (arg.startsWith("--flavor=")) {
                mFlavor = arg.substring(arg.indexOf('=') + 1);
            } else if (arg.startsWith("--buildType=")) {
                mBuildType = arg.substring(arg.indexOf('=') + 1);
            } else {
                System.err.println("Unknown option '" + arg + "'");
                throw new UsageException();
            }
        }

        // We expect at least one more argument (file name).
        int fileCount = args.length - idx;
        if (fileCount != 1) {
            throw new UsageException();
        }
        String[] inputFileNames = new String[fileCount];
        System.arraycopy(args, idx, inputFileNames, 0, fileCount);

        if (mAndroidJar == null || mFlavor == null || mBuildType == null) {
            throw new UsageException();
        }
        return inputFileNames;
    }

    private void usage() {
        System.err.print(
            "ApkConfigChecker: Check apk's config v1.0\n" +
            "Usage: apk-config-checker [args] <file.{dex,apk}>\n" +
            "Args(required):\n" +
            "  --androidjar={Path to sdk/platforms/android-{version}/androidjar.jar}\n" +
            "  --flavor={flavor name to check}\n" +
            "  --buildType={build type to check}\n"
        );
    }

    /**
     * Checks if input files array contain directories and
     * adds it's contents to the file list if so.
     * Otherwise just adds a file to the list.
     *
     * @return a List of file names to process
     */
    private List<String> collectFileNames(String[] inputFileNames) {
        List<String> fileNames = new ArrayList<String>();
        for (String inputFileName : inputFileNames) {
            File file = new File(inputFileName);
            if (file.isDirectory()) {
                throw new IllegalArgumentException("Only support file input(dex or apk).");
            } else {
                fileNames.add(inputFileName);
            }
        }
        return fileNames;
    }

    private static class UsageException extends RuntimeException {}
}
