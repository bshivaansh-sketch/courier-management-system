package courier;

import java.sql.*;
import java.util.*;

/** MySQL/JDBC persistence adapter. The payload preserves the application's domain JSON;
 * indexed relational columns make records easy to inspect and query in MySQL Workbench. */
public final class MySQLPersistence implements Persistence {
    private final String url, user, password, databaseName;
    private long revision = -1;
    private Map<String,Object> previous;

    public MySQLPersistence(String url, String user, String password, String databaseName) throws Exception {
        this.url=url; this.user=user; this.password=password; this.databaseName=databaseName;
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection c=connection(); Statement s=c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS workspace_metadata (id VARCHAR(40) PRIMARY KEY, schema_version INT NOT NULL, revision BIGINT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS customers (id VARCHAR(80) PRIMARY KEY, name VARCHAR(200) NOT NULL, phone VARCHAR(30) NOT NULL, email VARCHAR(254) NOT NULL UNIQUE, payload LONGTEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS agents (id VARCHAR(80) PRIMARY KEY, name VARCHAR(200) NOT NULL, phone VARCHAR(30) NOT NULL, email VARCHAR(254) NOT NULL UNIQUE, payload LONGTEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS accounts (email VARCHAR(254) PRIMARY KEY, account_role VARCHAR(20) NOT NULL, customer_id VARCHAR(80) NULL, payload LONGTEXT NOT NULL, INDEX idx_accounts_customer (customer_id))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS parcels (tracking_number VARCHAR(80) PRIMARY KEY, customer_id VARCHAR(80) NOT NULL, agent_id VARCHAR(80) NULL, status VARCHAR(40) NOT NULL, receiver_name VARCHAR(200) NOT NULL, type VARCHAR(40) NOT NULL, payload LONGTEXT NOT NULL, INDEX idx_parcels_customer (customer_id), INDEX idx_parcels_agent (agent_id), INDEX idx_parcels_status (status))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS tracking_events (tracking_number VARCHAR(80) NOT NULL, event_order INT NOT NULL, status VARCHAR(40) NOT NULL, location VARCHAR(200) NOT NULL, event_timestamp VARCHAR(60) NOT NULL, PRIMARY KEY (tracking_number,event_order), INDEX idx_tracking_status (status))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS payments (payment_id VARCHAR(100) PRIMARY KEY, tracking_number VARCHAR(80) NOT NULL UNIQUE, amount DECIMAL(12,2) NOT NULL, status VARCHAR(30) NOT NULL, method VARCHAR(30) NOT NULL, payment_timestamp VARCHAR(60) NOT NULL)");
        }
    }
    private Connection connection() throws SQLException { return DriverManager.getConnection(url,user,password); }
    @Override public synchronized void healthCheck() throws Exception { try(Connection c=connection();Statement s=c.createStatement()){s.executeQuery("SELECT 1");} }

    @Override public synchronized Map<String,Object> load() throws Exception {
        try(Connection c=connection()) {
            Long rev=null;
            try(PreparedStatement p=c.prepareStatement("SELECT schema_version,revision FROM workspace_metadata WHERE id='workspace'");ResultSet r=p.executeQuery()) {
                if(r.next()){if(r.getInt(1)!=1)throw new IllegalStateException("Unsupported MySQL schema version.");rev=r.getLong(2);}
            }
            if(rev==null){
                try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT (SELECT COUNT(*) FROM customers)+(SELECT COUNT(*) FROM agents)+(SELECT COUNT(*) FROM parcels)+(SELECT COUNT(*) FROM accounts)")){r.next();if(r.getLong(1)>0)throw new IllegalStateException("MySQL contains records but no workspace metadata. Use a new/empty database for initial setup.");}
                revision=-1; previous=null; return null;
            }
            Map<String,Object> state=new LinkedHashMap<>();state.put("version",1);
            state.put("customers",readPayloads(c,"customers","id"));
            state.put("agents",readPayloads(c,"agents","id"));
            state.put("parcels",readPayloads(c,"parcels","tracking_number"));
            Map<String,Object> accounts=new LinkedHashMap<>();
            try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT email,payload FROM accounts ORDER BY email")){while(r.next())accounts.put(r.getString(1),parse(r.getString(2)));}
            state.put("accounts",accounts); revision=rev; previous=copy(state); return copy(state);
        }
    }
    private List<Map<String,Object>> readPayloads(Connection c,String table,String keyColumn)throws Exception {
        List<Map<String,Object>> rows=new ArrayList<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT payload FROM "+table+" ORDER BY "+keyColumn)){while(r.next())rows.add(parse(r.getString(1)));}
        return rows;
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> parse(String value){return (Map<String,Object>)Json.parse(value);}

    @Override public synchronized void save(Map<String,Object> snapshot) throws Exception {
        try(Connection c=connection()) {
            c.setAutoCommit(false);
            try {
                long next=revision<0?0:revision+1;
                if(revision<0){try(PreparedStatement p=c.prepareStatement("INSERT INTO workspace_metadata(id,schema_version,revision) VALUES('workspace',1,0)")){p.executeUpdate();}}
                else try(PreparedStatement p=c.prepareStatement("UPDATE workspace_metadata SET revision=? WHERE id='workspace' AND revision=?")){p.setLong(1,next);p.setLong(2,revision);if(p.executeUpdate()!=1)throw new Persistence.ConflictException();}
                try(Statement s=c.createStatement()){s.executeUpdate("DELETE FROM tracking_events");s.executeUpdate("DELETE FROM payments");s.executeUpdate("DELETE FROM parcels");s.executeUpdate("DELETE FROM accounts");s.executeUpdate("DELETE FROM customers");s.executeUpdate("DELETE FROM agents");}
                writePeople(c,"customers",list(snapshot,"customers")); writePeople(c,"agents",list(snapshot,"agents"));
                writeAccounts(c,map(snapshot,"accounts")); writeParcels(c,list(snapshot,"parcels"));
                c.commit(); revision=next; previous=copy(snapshot);
            } catch(Exception e){c.rollback();throw e;} finally {c.setAutoCommit(true);}
        }
    }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> list(Map<String,Object> s,String k){return (List<Map<String,Object>>)s.getOrDefault(k,List.of());}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Map<String,Object> s,String k){return (Map<String,Object>)s.getOrDefault(k,Map.of());}
    private void writePeople(Connection c,String table,List<Map<String,Object>> rows)throws Exception {
        String sql="customers".equals(table)?"INSERT INTO customers(id,name,phone,email,payload) VALUES(?,?,?,?,?)":"INSERT INTO agents(id,name,phone,email,payload) VALUES(?,?,?,?,?)";
        try(PreparedStatement p=c.prepareStatement(sql)){for(Map<String,Object> m:rows){p.setString(1,str(m,"id"));p.setString(2,str(m,"name"));p.setString(3,str(m,"phone"));p.setString(4,str(m,"email"));p.setString(5,Json.stringify(m));p.addBatch();}p.executeBatch();}
    }
    private void writeAccounts(Connection c,Map<String,Object> accounts)throws Exception {
        try(PreparedStatement p=c.prepareStatement("INSERT INTO accounts(email,account_role,customer_id,payload) VALUES(?,?,?,?)")){for(var e:accounts.entrySet()){@SuppressWarnings("unchecked") Map<String,Object> a=(Map<String,Object>)e.getValue();p.setString(1,e.getKey());p.setString(2,str(a,"role"));String cid=str(a,"customerId");if(cid.isBlank())p.setNull(3,Types.VARCHAR);else p.setString(3,cid);p.setString(4,Json.stringify(a));p.addBatch();}p.executeBatch();}
    }
    @SuppressWarnings("unchecked") private void writeParcels(Connection c,List<Map<String,Object>> parcels)throws Exception {
        try(PreparedStatement p=c.prepareStatement("INSERT INTO parcels(tracking_number,customer_id,agent_id,status,receiver_name,type,payload) VALUES(?,?,?,?,?,?,?)");
            PreparedStatement t=c.prepareStatement("INSERT INTO tracking_events(tracking_number,event_order,status,location,event_timestamp) VALUES(?,?,?,?,?)");
            PreparedStatement pay=c.prepareStatement("INSERT INTO payments(payment_id,tracking_number,amount,status,method,payment_timestamp) VALUES(?,?,?,?,?,?)")) {
            for(Map<String,Object> m:parcels){String id=str(m,"trackingNumber"),agent=str(m,"agentId");p.setString(1,id);p.setString(2,str(m,"customerId"));if(agent.isBlank())p.setNull(3,Types.VARCHAR);else p.setString(3,agent);p.setString(4,str(m,"status"));p.setString(5,str(m,"receiverName"));p.setString(6,str(m,"type"));p.setString(7,Json.stringify(m));p.addBatch();
                Object history=m.get("history");if(history instanceof List<?> events){int i=0;for(Object o:events){Map<String,Object> ev=(Map<String,Object>)o;t.setString(1,id);t.setInt(2,i++);t.setString(3,str(ev,"status"));t.setString(4,str(ev,"location"));t.setString(5,str(ev,"timestamp"));t.addBatch();}}
                Object payment=m.get("payment");if(payment instanceof Map<?,?>){Map<String,Object> pm=(Map<String,Object>)payment;pay.setString(1,str(pm,"id"));pay.setString(2,id);pay.setBigDecimal(3,new java.math.BigDecimal(pm.get("amount").toString()));pay.setString(4,str(pm,"status"));pay.setString(5,str(pm,"method"));pay.setString(6,str(pm,"timestamp"));pay.addBatch();}
            }p.executeBatch();t.executeBatch();pay.executeBatch();
        }
    }
    private static String str(Map<String,Object> m,String k){return Objects.toString(m.get(k),"");}
    @SuppressWarnings("unchecked") private static Map<String,Object> copy(Map<String,Object> s){return s==null?null:(Map<String,Object>)Json.parse(Json.stringify(s));}
}
