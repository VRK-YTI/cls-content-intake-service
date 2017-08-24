package fi.vm.yti.cls.intake.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;


public abstract class FileUtils {

    /**
     * Skips the possible BOM character in the beginning of reader.
     *
     * @param reader Reader.
     * @throws IOException
     */
    @SuppressFBWarnings("DLS_DEAD_LOCAL_STORE")
    public static void skipBom(final Reader reader) throws IOException {

        reader.mark(1);
        final char[] possibleBOM = new char[1];
        final int readBytes = reader.read(possibleBOM);

        if (possibleBOM[0] != '\ufeff') {
            reader.reset();
        }

    }


    /**
     * Loads a file from classpath inside the application JAR.
     *
     * @param fileName The name of the file to be loaded.
     */
    public static InputStream loadFileFromClassPath(final String fileName) throws IOException {

        final ClassPathResource classPathResource = new ClassPathResource(fileName);
        final InputStream inputStream = classPathResource.getInputStream();
        return inputStream;

    }

}
