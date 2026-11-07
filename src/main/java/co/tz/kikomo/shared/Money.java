package co.tz.kikomo.shared;
import java.util.regex.*;
public class Money {
  private static final Pattern P=Pattern.compile("^(0|[1-9]\\d*)(\\.\\d{1,2})?$");
  public static long parseMinor(String s){
    if(s==null||!P.matcher(s.trim()).matches()) throw new IllegalArgumentException("INVALID_AMOUNT_FORMAT");
    String t=s.trim(); int d=t.indexOf('.');
    if(d<0) return Long.parseLong(t)*100;
    long whole=Long.parseLong(t.substring(0,d));
    String frac=t.substring(d+1); if(frac.length()==1) frac+="0";
    return whole*100+Long.parseLong(frac);
  }
  public static String formatMinor(long minor){ return (minor/100)+"."+String.format("%02d",Math.abs(minor%100)); }
  public static boolean isValidFormat(String s){ return s!=null && P.matcher(s.trim()).matches(); }
}
