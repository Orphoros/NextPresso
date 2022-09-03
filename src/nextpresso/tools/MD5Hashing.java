package nextpresso.tools;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5 hashing for file fingerprints
 */
public class MD5Hashing {
    /**
     * Gets the MD5 hash of a file
     * @param filePath Absolute path of the file to hash
     * @return MD5 hash of the file in string format
     * @throws IOException Thrown if there was an issue managing the file IO
     */
    public static String getHash(String filePath) throws IOException {
        try {
            byte[] byteBuffer = new byte[8192];
            MessageDigest digest = MessageDigest.getInstance("MD5");
            FileInputStream fis = new FileInputStream(filePath);
            int numRead;
            do {
                numRead = fis.read(byteBuffer);
                if (numRead > 0) {
                    digest.update(byteBuffer, 0, numRead);
                }
            } while (numRead != -1);
            fis.close();
            StringBuilder hash = new StringBuilder();
            for (byte b : digest.digest()) hash.append(String.format("%02x", b));
            return hash.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }
}
