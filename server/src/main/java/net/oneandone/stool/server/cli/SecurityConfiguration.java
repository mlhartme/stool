package net.oneandone.stool.server.cli;

import jnr.posix.Crypt;
import net.oneandone.stool.server.util.Server;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.LdapShaPasswordEncoder;
import org.springframework.security.crypto.password.MessageDigestPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.InetOrgPersonContextMapper;
import org.springframework.security.ldap.userdetails.LdapUserDetailsService;

import java.security.MessageDigest;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
    public static String REALM = "STOOL";


    @Autowired
    private Server server;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
           .csrf().disable()
           .authorizeRequests()
              .antMatchers("/api/**").hasRole("USER")
              .and()
           .httpBasic().realmName(REALM);
    }

    /* To allow Pre-flight [OPTIONS] request from browser */
    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers(HttpMethod.OPTIONS, "/**");
    }


    //--

    @Bean
    public DefaultSpringSecurityContextSource contextSource() {
        DefaultSpringSecurityContextSource contextSource;
        String url;

        url = server.configuration.ldapUrl;
        if (url.isEmpty()) {
            // will never be used - this is just to satisfy parameter checks in the constructor
            url = "ldap://localhost";
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
        String unit;
        FilterBasedLdapUserSearch userSearch;
        DefaultLdapAuthoritiesPopulator authoritiesPopulator;
        LdapUserDetailsService result;

        unit = server.configuration.ldapUnit;
        userSearch = new FilterBasedLdapUserSearch("ou=" + unit, "(uid={0})", contextSource());
        authoritiesPopulator = new DefaultLdapAuthoritiesPopulator(contextSource(), "ou=roles,ou=" + unit);
        authoritiesPopulator.setGroupSearchFilter("(member=uid={1})");
        authoritiesPopulator.setGroupRoleAttribute("ou");
        authoritiesPopulator.setSearchSubtree(false);
        authoritiesPopulator.setIgnorePartialResultException(true);

        result = new LdapUserDetailsService(userSearch, authoritiesPopulator);
        result.setUserDetailsMapper(new InetOrgPersonContextMapper());
        return result;
    }

}
