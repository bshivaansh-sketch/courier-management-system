package courier;

import java.time.Instant;
import java.util.*;

/** Domain model for the proposal's class relationships and OOP concepts. */
public final class Domain {
    public static class ValidationException extends IllegalArgumentException { public ValidationException(String message){super(message);} }
    public static String required(Map<String,Object> m,String k){String s=Objects.toString(m.get(k),"").trim();if(s.isEmpty()||s.length()>300)throw new ValidationException("Please enter a valid "+k+".");return s;}
    public static String phone(Map<String,Object> m){String p=required(m,"phone");if(!p.matches("[+0-9 ()-]{7,20}"))throw new ValidationException("Enter a valid phone number.");return p;}
    public interface Trackable { List<Map<String,Object>> trackParcel(); }
    public interface Payable { void makePayment(String method); }
    public static abstract class User {
        private final String id; private String name,phone,email;
        protected User(String id,Map<String,Object> m){this.id=id;update(m);}
        public final void update(Map<String,Object> m){name=required(m,"name");phone=phone(m);email=required(m,"email").toLowerCase();if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new ValidationException("Enter a valid email address.");}
        public String getId(){return id;} public String getName(){return name;} public String getPhone(){return phone;} public String getEmail(){return email;}
        public Map<String,Object> details(){return new LinkedHashMap<>(Map.of("id",id,"name",name,"phone",phone,"email",email));}
    }
    public static final class Customer extends User { public Customer(String id,Map<String,Object> m){super(id,m);} }
    public static final class DeliveryAgent extends User { public DeliveryAgent(String id,Map<String,Object> m){super(id,m);} }
    public static final class Address {
        private final String houseNo,street,city,state,pincode;
        public Address(Map<String,Object> m){houseNo=required(m,"houseNo");street=required(m,"street");city=required(m,"city");state=required(m,"state");pincode=required(m,"pincode");if(!pincode.matches("[0-9]{6}"))throw new ValidationException("PIN code must contain 6 digits.");}
        public String getFullAddress(){return houseNo+", "+street+", "+city+", "+state+" "+pincode;}
        public Map<String,Object> details(){return Map.of("houseNo",houseNo,"street",street,"city",city,"state",state,"pincode",pincode);}
    }
    public static abstract class Courier {
        private final double baseCharge;
        protected Courier(double baseCharge){this.baseCharge=baseCharge;} public double getBaseCharge(){return baseCharge;}
        public abstract double calculateCharge(double weight);
        /** Overload uses the same runtime-dispatched calculation. */
        public double calculateCharge(Parcel parcel){return calculateCharge(parcel.getWeight());}
        protected double check(double weight){if(!Double.isFinite(weight)||weight<=0||weight>100)throw new ValidationException("Weight must be greater than 0 and at most 100 kg.");return weight;}
    }
    public static final class StandardCourier extends Courier {public StandardCourier(){super(50);} @Override public double calculateCharge(double w){return Math.round((getBaseCharge()+30*check(w))*100)/100.0;} }
    public static final class ExpressCourier extends Courier {private final double extraCharge=150;public ExpressCourier(){super(50);}@Override public double calculateCharge(double w){return Math.round((getBaseCharge()+30*check(w)+extraCharge)*100)/100.0;} }
    public static Courier service(String type){return switch(type){case "Express"->new ExpressCourier();case "Standard"->new StandardCourier();default->throw new ValidationException("Choose Standard or Express courier.");};}
    public static final List<String> STATUSES=List.of("Booked","Picked Up","In Transit","Out for Delivery","Delivered","Cancelled");
    public static final class Tracking {
        private final String status,location,timestamp;
        public Tracking(String status,String location){this(status,location,Instant.now().toString());}
        public Tracking(String status,String location,String timestamp){this.status=status;this.location=location;this.timestamp=timestamp;}
        public Map<String,Object> details(){return Map.of("status",status,"location",location,"timestamp",timestamp);}
    }
    public static final class Payment implements Payable {
        private final String id; private final double amount; private String status="Pending",method="",timestamp="";
        public Payment(String id,double amount){this.id=id;this.amount=amount;}
        @Override public void makePayment(String method){if(status.equals("Paid"))throw new ValidationException("This parcel has already been paid.");if(!List.of("Cash","UPI","Card").contains(method))throw new ValidationException("Choose a valid payment method.");this.method=method;status="Paid";timestamp=Instant.now().toString();}
        public boolean isPaid(){return status.equals("Paid");}
        public Map<String,Object> getReceipt(){return Map.of("id",id,"amount",amount,"status",status,"method",method,"timestamp",timestamp);}
        public void restore(Map<String,Object> m){status=m.get("status").toString();method=m.get("method").toString();timestamp=m.get("timestamp").toString();}
    }
    public static final class Parcel implements Trackable {
        private final String trackingNumber,customerId,receiverName,receiverPhone,type,description;
        private final Address sender,receiver; private final double weight; private String status="Booked",agentId="";
        private final ArrayList<Tracking> history=new ArrayList<>(); private final Payment payment;
        @SuppressWarnings("unchecked") public Parcel(String id,Map<String,Object> m){trackingNumber=id;customerId=required(m,"customerId");receiverName=required(m,"receiverName");receiverPhone=required(m,"receiverPhone");if(!receiverPhone.matches("[+0-9 ()-]{7,20}"))throw new ValidationException("Enter a valid receiver phone.");type=required(m,"type");weight=Double.parseDouble(Objects.toString(m.get("weight"),"0"));description=Objects.toString(m.get("description"),"Parcel");sender=new Address((Map<String,Object>)m.get("sender"));receiver=new Address((Map<String,Object>)m.get("receiver"));payment=new Payment("PAY-"+id,service(type).calculateCharge(this));history.add(new Tracking(status,sender.city));}
        public double getWeight(){return weight;} public String getCustomerId(){return customerId;}public String getTrackingNumber(){return trackingNumber;}public String getStatus(){return status;}public String getAgentId(){return agentId;}public Payment getPayment(){return payment;}
        public void assignParcel(String agent){if(List.of("Delivered","Cancelled").contains(status))throw new ValidationException("Completed parcels cannot be assigned.");agentId=agent;}
        public void updateStatus(String next,String location){if(!STATUSES.contains(next)||List.of("Delivered","Cancelled").contains(status)||next.equals("Booked")||(!next.equals("Cancelled")&&STATUSES.indexOf(next)!=STATUSES.indexOf(status)+1))throw new ValidationException("Please follow the delivery status order.");if(!next.equals("Cancelled")&&(agentId.isBlank()||!payment.isPaid()))throw new ValidationException("Record payment and assign an agent before pickup.");status=next;history.add(new Tracking(status,location));}
        @Override public List<Map<String,Object>> trackParcel(){return history.stream().map(Tracking::details).toList();}
        public Map<String,Object> getDetails(){Map<String,Object> m=new LinkedHashMap<>();m.put("trackingNumber",trackingNumber);m.put("customerId",customerId);m.put("receiverName",receiverName);m.put("receiverPhone",receiverPhone);m.put("type",type);m.put("weight",weight);m.put("description",description);m.put("sender",sender.details());m.put("receiver",receiver.details());m.put("status",status);m.put("agentId",agentId);m.put("history",trackParcel());m.put("payment",payment.getReceipt());return m;}
        @SuppressWarnings("unchecked") public void restore(Map<String,Object> m){status=m.get("status").toString();agentId=m.get("agentId").toString();payment.restore((Map<String,Object>)m.get("payment"));history.clear();for(Object o:(List<Object>)m.get("history")){Map<String,Object> t=(Map<String,Object>)o;history.add(new Tracking(t.get("status").toString(),t.get("location").toString(),t.get("timestamp").toString()));}}
    }
}
