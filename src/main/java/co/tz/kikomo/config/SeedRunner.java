package co.tz.kikomo.config;
import co.tz.kikomo.db.Repositories; import co.tz.kikomo.shared.Policy; import com.fasterxml.jackson.databind.ObjectMapper; import org.springframework.beans.factory.annotation.Value; import org.springframework.boot.CommandLineRunner; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Component; import java.util.*;
@Component
public class SeedRunner implements CommandLineRunner {
  private final JdbcTemplate jdbc; private final Repositories repo; private final ObjectMapper mapper=new ObjectMapper();
  @Value("${DEMO_AGENT_TOKEN:demo-token-change-me-32chars!!}") String demoToken;
  @Value("${DEMO_PAYEE_DEVTOOLS:devtools@example.com}") String payee1;
  @Value("${DEMO_PAYEE_CONF:conf@example.com}") String payee2;
  public SeedRunner(JdbcTemplate jdbc, Repositories repo){ this.jdbc=jdbc; this.repo=repo; }
  public void run(String... args){
    try{
      Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM agents WHERE token_hash=?", Integer.class, Repositories.hashToken(demoToken));
      UUID agentId;
      if(count!=null&&count>0){ agentId=(UUID)jdbc.queryForList("SELECT id FROM agents WHERE token_hash=?", Repositories.hashToken(demoToken)).get(0).get("id"); }
      else { agentId=UUID.randomUUID(); jdbc.update("INSERT INTO agents(id,name,token_hash,status) VALUES (?,?,?,'active')", agentId, "demo-agent", Repositories.hashToken(demoToken)); }
      if(repo.activePolicy(agentId)==null){ repo.savePolicy(agentId, Policy.demo(List.of(payee1,payee2)), "demo policy", "active"); }
      System.out.println("Seeded demo agent "+agentId);
    }catch(Exception e){ System.out.println("Seed skipped: "+e.getMessage()); }
  }
}
