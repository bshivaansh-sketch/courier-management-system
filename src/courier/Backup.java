package courier;
import java.nio.file.*;

/** Portable JSON export/import using the active MySQL database. */
public final class Backup {
    public static void main(String[] args) throws Exception {
        if(args.length!=2||!java.util.List.of("export","import").contains(args[0]))
            throw new IllegalArgumentException("Usage: courier.Backup export|import path.json");
        Path file=Path.of(args[1]);
        MySQLPersistence db=new MySQLPersistence(Config.mysqlUrl(),Config.mysqlUser(),Config.mysqlPassword(),Config.database());
        try {
            if(args[0].equals("import")) {
                if(!Files.isRegularFile(file))throw new IllegalArgumentException("Backup file not found.");
                if(db.load()!=null)throw new IllegalStateException("Import requires an empty database. Existing records will not be overwritten.");
                try(Store store=new Store(db,file)){System.out.println("Backup imported into MySQL.");}
            } else {
                var snapshot=db.load();
                if(snapshot==null)throw new IllegalStateException("Nothing to export: MySQL database is empty.");
                new FilePersistence(file).save(snapshot);
                System.out.println("Backup exported to "+file.toAbsolutePath());
                db.close();
            }
        } catch(Exception e){try{db.close();}catch(Exception ignored){}throw e;}
    }
}
