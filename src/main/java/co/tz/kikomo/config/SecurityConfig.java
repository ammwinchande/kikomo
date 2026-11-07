package co.tz.kikomo.config;
import org.springframework.beans.factory.annotation.Value; import org.springframework.context.annotation.*; import org.springframework.security.config.annotation.web.builders.HttpSecurity; import org.springframework.security.web.SecurityFilterChain; import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter; import jakarta.servlet.*; import jakarta.servlet.http.*; import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; import org.springframework.security.core.context.SecurityContextHolder; import java.util.List;
@Configuration
public class SecurityConfig {
  @Value("${ADMIN_PASSWORD:admin}") String adminPassword;
  @Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
    http.csrf(c->c.disable())
      .authorizeHttpRequests(a->a.requestMatchers("/api/auth/login","/api/health","/mcp","/demo/**","/","/index.html","/assets/**","/*.js","/*.css","/favicon.ico").permitAll().requestMatchers("/api/**").authenticated().anyRequest().permitAll())
      .addFilterBefore(new Filter(){
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
          HttpServletRequest r=(HttpServletRequest)req;
          // cookie session: KIKOMO_ADMIN=ok-<password hash marker> set at login; simple check
          if(r.getCookies()!=null) for(Cookie c: r.getCookies()) if("KIKOMO_ADMIN".equals(c.getName()) && c.getValue()!=null && c.getValue().startsWith("ok-")){
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin",null,List.of())); }
          chain.doFilter(req,res);
        }}, UsernamePasswordAuthenticationFilter.class)
      .httpBasic(b->b.disable()).formLogin(f->f.disable());
    return http.build();
  }
}
