package co.tz.kikomo.engine;
import java.text.Normalizer; import java.util.regex.*;
public class Normalize {
  private static final Pattern EMAIL=Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$");
  public static String payee(String raw){
    if(raw==null) return "";
    String s=Normalizer.normalize(raw, Normalizer.Form.NFKC).trim().toLowerCase();
    return s;
  }
  public static boolean isValidEmail(String normalized){ return normalized!=null && EMAIL.matcher(normalized).matches(); }
}
