package courier;

import static courier.Domain.*;
import java.nio.file.*;
import java.security.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.*;

/** MySQL-backed domain records with separate JSON backups and migration. */
public final class Store implements AutoCloseable {
    private final Persistence persistence;
    private final LinkedHashMap<String,Customer> customers=new LinkedHashMap<>();
    private final LinkedHashMap<String,DeliveryAgent> agents=new LinkedHashMap<>();
    private final LinkedHashMap<String,Parcel> parcels=new LinkedHashMap<>();
    private final LinkedHashMap<String,Map<String,Object>> accounts=new LinkedHashMap<>();
    public Store(Path file) throws Exception {this(new FilePersistence(file),null);}
    public Store(Persistence persistence,Path legacy) throws Exception {
        this.persistence=persistence;Map<String,Object> existing=persistence.load();
        if(existing!=null)load(Json.stringify(existing));
        else {if(legacy!=null&&Files.exists(legacy)){load(Files.readString(legacy));System.out.println("Migrating existing JSON records into MySQL.");}else seed();save();}
    }
    private void reload()throws Exception {Map<String,Object> existing=persistence.load();if(existing==null)throw new IllegalStateException("Database records are missing.");load(Json.stringify(existing));}
    public synchronized void exportBackup(Path file)throws Exception {reload();new FilePersistence(file).save(snapshot());}
    @Override public void close(){persistence.close();}
    public synchronized void healthCheck()throws Exception {persistence.healthCheck();}
    public static String hash(String password,String salt) throws Exception {return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(password.toCharArray(),Base64.getDecoder().decode(salt),120000,256)).getEncoded());}
    private void account(String email,String password,String role,String customerId)throws Exception{if(password.length()<8)throw new ValidationException("Password must contain at least 8 characters.");byte[]salt=new byte[16];new SecureRandom().nextBytes(salt);String s=Base64.getEncoder().encodeToString(salt);accounts.put(email,Map.of("salt",s,"hash",hash(password,s),"role",role,"customerId",customerId));}
    public synchronized Map<String,Object> login(Map<String,Object> m)throws Exception{reload();String email=required(m,"email").toLowerCase();Map<String,Object>a=accounts.get(email);if(a==null||!MessageDigest.isEqual(hash(required(m,"password"),a.get("salt").toString()).getBytes(),a.get("hash").toString().getBytes()))throw new ValidationException("Email or password is incorrect.");return Map.of("email",email,"role",a.get("role"),"customerId",a.get("customerId"),"name",a.get("role").equals("admin")?"Alex Morgan":customers.get(a.get("customerId")).getName());}
    public synchronized Map<String,Object> view(Map<String,Object> user)throws Exception{reload();boolean admin=user.get("role").equals("admin");String cid=user.get("customerId").toString();return Map.of("customers",customers.values().stream().filter(c->admin||c.getId().equals(cid)).map(Customer::details).toList(),"agents",admin?agents.values().stream().map(DeliveryAgent::details).toList():List.of(),"parcels",parcels.values().stream().filter(p->admin||p.getCustomerId().equals(cid)).map(Parcel::getDetails).toList(),"user",user);}
    public synchronized Object quote(Map<String,Object> m){return Map.of("amount",service(required(m,"type")).calculateCharge(Double.parseDouble(required(m,"weight"))));}
    public synchronized Object mutate(Map<String,Object> m,Map<String,Object> user)throws Exception{
        reload();
        String before=Json.stringify(snapshot());
        try {Object result=change(m,user);save();return result;}catch(Exception e){load(before);throw e;}
    }
    private Object change(Map<String,Object> m,Map<String,Object> user)throws Exception{
        String op=required(m,"op");boolean admin=user!=null&&user.get("role").equals("admin");
        if(op.equals("register")){String email=required(m,"email").toLowerCase();if(accounts.containsKey(email)||customers.values().stream().anyMatch(c->c.getEmail().equals(email)))throw new ValidationException("This email is already registered.");String id="CUS-"+UUID.randomUUID().toString().substring(0,8);Customer c=new Customer(id,m);account(email,required(m,"password"),"customer",id);customers.put(id,c);return c.details();}
        if(user==null)throw new ValidationException("Please sign in.");
        if(op.equals("book")){if(!admin)m.put("customerId",user.get("customerId"));if(!customers.containsKey(required(m,"customerId")))throw new ValidationException("Select an existing customer.");String id="CR"+java.time.LocalDate.now().getYear()+String.format("%05d",parcels.size()+10001);while(parcels.containsKey(id))id="CR"+UUID.randomUUID().toString().substring(0,12).toUpperCase();Parcel p=new Parcel(id,m);parcels.put(id,p);return p.getDetails();}
        if(op.equals("customer")||op.equals("agent")){if(!admin)throw new ValidationException("Administrator access required.");String id=Objects.toString(m.get("id"),"");boolean create=id.isBlank();if(create)id=(op.equals("customer")?"CUS-":"AGT-")+UUID.randomUUID().toString().substring(0,8);if(op.equals("customer")){final String currentId=id;String email=required(m,"email").toLowerCase();if(customers.values().stream().anyMatch(c->c.getEmail().equals(email)&&!c.getId().equals(currentId)))throw new ValidationException("Customer email already exists.");Customer old=customers.get(id);Customer c=new Customer(id,m);if(!create&&old==null)throw new ValidationException("Customer not found.");if(old!=null&&accounts.containsKey(old.getEmail())&&!old.getEmail().equals(email)){if(accounts.containsKey(email))throw new ValidationException("Email already in use.");accounts.put(email,accounts.remove(old.getEmail()));}customers.put(id,c);return c.details();}else{if(!create&&!agents.containsKey(id))throw new ValidationException("Agent not found.");DeliveryAgent a=new DeliveryAgent(id,m);agents.put(id,a);return a.details();}}
        Parcel p=parcels.get(required(m,"trackingNumber"));if(p==null)throw new ValidationException("Parcel not found.");if(!admin&&!p.getCustomerId().equals(user.get("customerId")))throw new ValidationException("Parcel not found.");
        switch(op){case "pay"->{if(p.getStatus().equals("Cancelled"))throw new ValidationException("Cancelled parcels cannot be paid.");p.getPayment().makePayment(required(m,"method"));}case "assign"->{if(!admin)throw new ValidationException("Administrator access required.");String aid=required(m,"agentId");if(!agents.containsKey(aid))throw new ValidationException("Agent not found.");p.assignParcel(aid);}case "status"->{String next=required(m,"status");if(!admin&&!next.equals("Cancelled"))throw new ValidationException("Administrator access required.");if(!admin&&!p.getStatus().equals("Booked"))throw new ValidationException("Only booked parcels may be cancelled by customers.");p.updateStatus(next,required(m,"location"));}default->throw new ValidationException("Unknown operation.");}return p.getDetails();
    }
    private Map<String,Object> snapshot(){return Map.of("version",1,"customers",customers.values().stream().map(Customer::details).toList(),"agents",agents.values().stream().map(DeliveryAgent::details).toList(),"parcels",parcels.values().stream().map(Parcel::getDetails).toList(),"accounts",accounts);}
    private void save()throws Exception{persistence.save(snapshot());}
    @SuppressWarnings("unchecked") private void load(String json){Map<String,Object>m=(Map<String,Object>)Json.parse(json);customers.clear();agents.clear();parcels.clear();accounts.clear();for(Object x:(List<Object>)m.get("customers")){Map<String,Object>c=(Map<String,Object>)x;customers.put(c.get("id").toString(),new Customer(c.get("id").toString(),c));}for(Object x:(List<Object>)m.get("agents")){Map<String,Object>a=(Map<String,Object>)x;agents.put(a.get("id").toString(),new DeliveryAgent(a.get("id").toString(),a));}for(Object x:(List<Object>)m.get("parcels")){Map<String,Object>p=(Map<String,Object>)x;Parcel parcel=new Parcel(p.get("trackingNumber").toString(),p);parcel.restore(p);parcels.put(parcel.getTrackingNumber(),parcel);}accounts.putAll((Map<String,Map<String,Object>>)m.get("accounts"));}
    private void seed()throws Exception{
        String adminEmail=Config.get("ADMIN_EMAIL","admin@parcelly.local").trim().toLowerCase();
        if(!adminEmail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new ValidationException("ADMIN_EMAIL must be a valid email address.");
        String adminPassword=Config.get("ADMIN_PASSWORD","");
        if(adminPassword.isEmpty()){
            if(Boolean.parseBoolean(Config.get("COOKIE_SECURE","false")))throw new ValidationException("Set ADMIN_PASSWORD before creating a production workspace.");
            adminPassword="Admin@123";
        }
        account(adminEmail,adminPassword,"admin","");
        if(!Boolean.parseBoolean(Config.get("SEED_DEMO_DATA","true")))return;
        String[]names={"Rahul Sharma","Priya Patel","Arjun Mehta","Ananya Rao","Vikram Singh","Neha Kapoor"};String[]cities={"Mumbai","Bengaluru","Delhi","Pune","Chennai","Hyderabad"};
        for(int i=0;i<names.length;i++)customers.put("CUS-10"+i,new Customer("CUS-10"+i,Map.of("name",names[i],"phone","98765432"+String.format("%02d",i),"email",names[i].split(" ")[0].toLowerCase()+"@example.com")));
        account("rahul@example.com","Customer@123","customer","CUS-100");
        String[]an={"Rohan Desai","Meera Joshi","Kabir Khan"};for(int i=0;i<3;i++)agents.put("AGT-10"+i,new DeliveryAgent("AGT-10"+i,Map.of("name",an[i],"phone","912345670"+i,"email","agent"+i+"@example.com")));
        for(int i=0;i<6;i++){Map<String,Object>m=new HashMap<>(Map.of("customerId","CUS-10"+i,"receiverName",names[(i+2)%6],"receiverPhone","9876543210","type",i%2==0?"Express":"Standard","weight",i==0?5:1.5+i,"description",new String[]{"Design samples","Books & stationery","Home essentials","Handcrafted ceramics","Office supplies","Personal belongings"}[i],"sender",Map.of("houseNo","24","street","Park Avenue","city",cities[i],"state","Maharashtra","pincode","400001"),"receiver",Map.of("houseNo","18B","street","MG Road","city",cities[(i+1)%6],"state","Karnataka","pincode","560001")));Parcel p=new Parcel("CR2026"+String.format("%05d",10001+i),m);parcels.put(p.getTrackingNumber(),p);if(i!=5){p.getPayment().makePayment(i%2==0?"UPI":"Cash");p.assignParcel("AGT-10"+(i%3));for(int j=1;j<=new int[]{2,4,1,3,4}[i];j++)p.updateStatus(STATUSES.get(j),cities[(i+1)%6]);}}
    }
}

