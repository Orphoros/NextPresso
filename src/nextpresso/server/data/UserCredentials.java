package nextpresso.server.data;

import java.util.HashMap;

/**
 * User credential database
 */
public class UserCredentials {
    public static final HashMap<String, String> dataSet = new HashMap<>();

    static {
        //128 bits, 16 bits of salt, 100 iterations
        dataSet.put("Bob", "100:86f9e2d8ef2edd0afb78a2cc702dcf98:e2be23bdd5e09982c7c72108275a6d78");    //PW:     PWBob1234!
        dataSet.put("Alice", "100:a5a2fb65e1a0f4bedccfe04993f5583f:94397e3eade0511006ca3d31960f29e9");  //PW:     PWAlice1234!
        dataSet.put("Jack", "100:aefd9e0b87e779663443885d631f9c8b:b9f0e34b2b4a422c70b5ddeff071590c");   //PW:     PWJack1234!
    }
}
