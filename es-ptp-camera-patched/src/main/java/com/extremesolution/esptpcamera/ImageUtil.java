/**
 * Copyright 2013 Nils Assbeck, Guersel Ayaz and Michael Zoech
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.extremesolution.esptpcamera;

/**
 * Utility class for image-related operations.
 */
public class ImageUtil {

    private static final String[] RAW_EXTENSIONS = {
            ".CR2", ".NRW", ".EIP", ".RAF",
            ".ERF", ".RW2", ".RWZ", ".ARW",
            ".NEF", ".DNG", ".K25", ".ARI",
            ".SRF", ".RAW", ".DCR", ".CRW",
            ".BAY", ".3RF", ".MEF", ".CS1",
            ".ORF", ".MOS", ".MFW", ".SR2",
            ".KDC", ".FFF", ".CR3", ".SRW",
            ".RWL", ".J6I", ".KC2", ".X3F",
            ".MRW", ".IIQ", ".PEF", ".CXI", ".MDC"
    };

    /**
     * Check if the given filename represents a RAW image format.
     *
     * @param filename The filename to check (should be uppercase)
     * @return true if the file is a RAW image format
     */
    public static boolean isRawImage(String filename) {
        if (filename == null) {
            return false;
        }
        String upperFilename = filename.toUpperCase();
        for (String extension : RAW_EXTENSIONS) {
            if (upperFilename.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }
}
