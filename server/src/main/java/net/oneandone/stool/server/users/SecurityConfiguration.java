package net.oneandone.stool.server.users;

import net.oneandone.stool.server.Server;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.LdapUserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
    public static String REALM = "STOOL";


    @Autowired
    private Server server;

    @Override
    public void configure(WebSecurity web) {
        if (server.configuration.ldapUrl.isEmpty()) {
            web.ignoring().anyRequest();
        } else {
            /* To allow Pre-flight [OPTIONS] request from browser */
            web.ignoring().antMatchers(HttpMethod.OPTIONS, "/**");
        }
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        if (server.configuration.ldapUrl.isEmpty()) {
            // disabled security
        } else {
            http
               .addFilterAfter(new TokenAuthenticationFilter(server.userManager), BasicAuthenticationFilter.class)
               .sessionManagement()
                  .disable()
               .csrf().disable() // no sessions -> no need to protect them with csrf
               .authorizeRequests()
                    .antMatchers("/api/**").fullyAuthenticated()
                    .and()
               .httpBasic().realmName(REALM);
        }
    }


    //--

    @Bean
    public DefaultSpringSecurityContextSource contextSource() {
        DefaultSpringSecurityContextSource contextSource;
        String url;

        url = server.configuration.ldapUrl;
        if (url.isEmpty()) {
            url = "ldap://will-no-be-used";
        }
        contextSource = new DefaultSpringSecurityContextSource(url);
        contextSource.setUserDn(server.configuration.ldapPrincipal);
        contextSource.setPassword(server.configuration.ldapCredentials);
        return contextSource;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new CryptPasswordEncoder();
    }

    @Bean
    @Override
    public UserDetailsService userDetailsService() {
        String url;
        String unit;
        FilterBasedLdapUserSearch userSearch;
        DefaultLdapAuthoritiesPopulator authoritiesPopulator;
        LdapUserDetailsService result;

        url = server.configuration.ldapUrl;
        if (url.isEmpty()) {
            return new InMemoryUserDetailsManager();
        }

        unit = server.configuration.ldapUnit;
        userSearch = new FilterBasedLdapUserSearch("ou=" + unit, "(uid={0})", contextSource());
        authoritiesPopulator = new DefaultLdapAuthoritiesPopulator(contextSource(), "ou=roles,ou=" + unit);
        authoritiesPopulator.setGroupSearchFilter("(member=uid={1})");
        authoritiesPopulator.setGroupRoleAttribute("ou");
        authoritiesPopulator.setSearchSubtree(false);
        authoritiesPopulator.setIgnorePartialResultException(true);

        result = new LdapUserDetailsService(userSearch, authoritiesPopulator);
        result.setUserDetailsMapper(new UserDetailsMapper());
        return result;
    }

}
