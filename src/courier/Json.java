package courier;

import java.util.*;

/** Small dependency-free JSON codec. Never evaluates input. */
public final class Json {
    public static Object parse(String text) { Parser p=new Parser(text); Object v=p.value(); p.ws(); if(p.i!=text.length()) throw new IllegalArgumentException("Invalid JSON"); return v; }
    public static String stringify(Object v) {
        if(v==null) return "null";
        if(v instanceof String s) { StringBuilder b=new StringBuilder("\""); for(char c:s.toCharArray()) { switch(c) {case '"' -> b.append("\\\""); case '\\' -> b.append("\\\\"); case '\n' -> b.append("\\n"); case '\r' -> b.append("\\r"); case '\t' -> b.append("\\t"); default -> { if(c<32)b.append(String.format("\\u%04x",(int)c)); else b.append(c); } } } return b.append('"').toString(); }
        if(v instanceof Number || v instanceof Boolean) return v.toString();
        if(v instanceof Map<?,?> m) { List<String> a=new ArrayList<>(); m.forEach((k,x)->a.add(stringify(k.toString())+":"+stringify(x))); return "{"+String.join(",",a)+"}"; }
        if(v instanceof Iterable<?> xs) { List<String>a=new ArrayList<>(); xs.forEach(x->a.add(stringify(x))); return "["+String.join(",",a)+"]"; }
        throw new IllegalArgumentException("Unsupported JSON value");
    }
    private static final class Parser {
        final String s; int i; Parser(String s){this.s=s;} void ws(){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;}
        char take(){if(i>=s.length())throw new IllegalArgumentException("Incomplete JSON");return s.charAt(i++);}
        Object value(){ws(); if(i>=s.length())throw new IllegalArgumentException("Missing JSON"); char c=s.charAt(i); if(c=='"')return str(); if(c=='{'){i++; Map<String,Object> m=new LinkedHashMap<>();ws();if(s.charAt(i)=='}'){i++;return m;}do{ws();String k=str();ws();if(take()!=':')throw new IllegalArgumentException("Invalid JSON");m.put(k,value());ws();c=take();}while(c==',');if(c!='}')throw new IllegalArgumentException("Invalid JSON");return m;}if(c=='['){i++;List<Object>a=new ArrayList<>();ws();if(s.charAt(i)==']'){i++;return a;}do{a.add(value());ws();c=take();}while(c==',');if(c!=']')throw new IllegalArgumentException("Invalid JSON");return a;}for(String literal:List.of("true","false","null")){if(s.startsWith(literal,i)){i+=literal.length();return literal.equals("null")?null:Boolean.valueOf(literal);}} int start=i;while(i<s.length()&&"-+0123456789.eE".indexOf(s.charAt(i))>=0)i++;if(start==i)throw new IllegalArgumentException("Invalid JSON");return Double.valueOf(s.substring(start,i));}
        String str(){if(take()!='"')throw new IllegalArgumentException("Expected string");StringBuilder b=new StringBuilder();while(true){char c=take();if(c=='"')return b.toString();if(c=='\\'){c=take();switch(c){case '"','\\','/'->b.append(c);case 'n'->b.append('\n');case 'r'->b.append('\r');case 't'->b.append('\t');case 'b'->b.append('\b');case 'f'->b.append('\f');case 'u'->{b.append((char)Integer.parseInt(s.substring(i,i+4),16));i+=4;}default->throw new IllegalArgumentException("Invalid escape");}}else {if(c<32)throw new IllegalArgumentException("Invalid string");b.append(c);}}}
    }
}
