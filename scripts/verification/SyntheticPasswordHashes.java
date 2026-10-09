import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCrypt;

/** Local synthetic fixture helper. Its input/output must be captured privately. */
class SyntheticPasswordHashes {
    public static void main(String[] args) throws Exception {
        BufferedReader input = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String password;
        while ((password = input.readLine()) != null) {
            if (!password.matches("[0-9a-f]{48}")) {
                throw new IllegalArgumentException("Invalid generated fixture input");
            }
            String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));
            if (!BCrypt.checkpw(password, hash)) {
                throw new IllegalStateException("Synthetic fixture hash verification failed");
            }
            System.out.println(hash);
        }
    }
}
