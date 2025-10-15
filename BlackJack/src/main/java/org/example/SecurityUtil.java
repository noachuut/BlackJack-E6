package org.example;
import org.mindrot.jbcrypt.BCrypt;
public class SecurityUtil {

    static String hashPwd(String raw) {
        return BCrypt.hashpw(raw, BCrypt.gensalt(12));
    }
    static boolean checkPwd(String raw, String hashed) {
        return BCrypt.checkpw(raw, hashed);
    }

}
