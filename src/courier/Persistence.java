package courier;
import java.util.Map;
/** Business rules do not depend on the database or backup format. */
public interface Persistence extends AutoCloseable {
    Map<String,Object> load() throws Exception;
    void save(Map<String,Object> snapshot) throws Exception;
    default void healthCheck() throws Exception {}
    @Override default void close() {}
    final class ConflictException extends RuntimeException {
        public ConflictException(){super("Records changed in another request. Please refresh and try again.");}
    }
}
