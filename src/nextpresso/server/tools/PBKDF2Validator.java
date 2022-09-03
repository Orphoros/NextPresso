package nextpresso.server.tools;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

/**
 * Validator for PBKDF2 hashes
 * @author https://gist.github.com/jtan189/3804290
 */
public class PBKDF2Validator {
    public static boolean validateHash(String data, String hash) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return validatePassword(data.toCharArray(), hash);
    }

    private static boolean validatePassword(char[] password, String goodHash) throws NoSuchAlgorithmException, InvalidKeySpecException {
        String[] params = goodHash.split(":");
        int iterations = Integer.parseInt(params[0]);
        byte[] salt = stringToBytes(params[1]);
        byte[] hash = stringToBytes(params[2]);

        byte[] testHash = pbkdf2(password, salt, iterations, hash.length);

        return bytesEqual(hash, testHash);
    }

    private static boolean bytesEqual(byte[] a, byte[] b) {
        int diff = a.length ^ b.length;
        for(int i = 0; i < a.length && i < b.length; i++)
            diff |= a[i] ^ b[i];
        return diff == 0;
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int bytes) throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, bytes * 8);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        return skf.generateSecret(spec).getEncoded();
    }

    private static byte[] stringToBytes(String hex) {
        byte[] binary = new byte[hex.length() / 2];
        for(int i = 0; i < binary.length; i++) binary[i] = (byte)Integer.parseInt(hex.substring(2*i, 2*i+2), 16);
        return binary;
    }

}
