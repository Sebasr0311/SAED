import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class HashCheck {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        boolean match = encoder.matches("password123", "$2a$10$hV0SOJvWQtYqvqEg7tKXqeolVuNjM4Y7BCUXxg1yVBvB4/gRi2xFe");
        System.out.println("Match: " + match);
    }
}
