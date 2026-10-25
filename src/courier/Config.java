package courier;
import java.nio.file.*;
import java.util.*;
/** Environment variables take precedence over an optional, git-ignored .env file. */
public final class Config {
    private static final Map<String,String> local=read();
    private static Map<String,String> read(){
        Map<String,String> values=new HashMap<>();Path file=Path.of(".env");
        if(Files.exists(file))try{for(String line:Files.readAllLines(file)){line=line.trim();if(line.isEmpty()||line.startsWith("#"))continue;int eq=line.indexOf('=');if(eq<1)throw new IllegalArgumentException("Invalid .env entry.");String key=line.substring(0,eq).trim(),value=line.substring(eq+1).trim();if(value.length()>=2&&((value.startsWith("\"")&&value.endsWith("\""))||(value.startsWith("'")&&value.endsWith("'"))))value=value.substring(1,value.length()-1);values.put(key,value);}}catch(java.io.IOException e){throw new IllegalStateException("Unable to read .env",e);}
        return values;
    }
    public static String get(String key,String fallback){String env=System.getenv(key);return env==null?local.getOrDefault(key,fallback):env;}
    public static String mysqlUrl(){return get("MYSQL_URL","jdbc:mysql://127.0.0.1:3306/parcelly?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");}
    public static String mysqlUser(){return get("MYSQL_USER","root");}
    public static String mysqlPassword(){return get("MYSQL_PASSWORD","");}
    public static String database(){String name=get("MYSQL_DATABASE","parcelly");if(!name.matches("[A-Za-z0-9_]{1,64}"))throw new IllegalArgumentException("Invalid MYSQL_DATABASE name.");return name;}
    /** Startup scripts use this to verify configuration without printing credentials. */
    public static void main(String[]args){System.out.println(database());}
}

