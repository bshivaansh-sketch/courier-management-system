package courier;
import java.nio.file.*;
import java.util.Map;
/** File handling for local backups, legacy migration, and isolated domain tests. */
public final class FilePersistence implements Persistence {
    private final Path file;
    public FilePersistence(Path file){this.file=file;}
    @SuppressWarnings("unchecked") public Map<String,Object> load()throws Exception {
        if(!Files.exists(file))return null;
        Object value=Json.parse(Files.readString(file));
        if(!(value instanceof Map))throw new IllegalArgumentException("Backup must contain a JSON object.");
        return (Map<String,Object>)value;
    }
    public void save(Map<String,Object> snapshot)throws Exception {
        Files.createDirectories(file.toAbsolutePath().getParent());Path tmp=file.resolveSibling(file.getFileName()+".tmp");
        Files.writeString(tmp,Json.stringify(snapshot));
        try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
    }
}
