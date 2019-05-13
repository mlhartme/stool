package net.oneandone.stool.server.users;

import net.oneandone.stool.server.Server;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.authentication.LdapAuthenticator;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.search.LdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.LdapUserDetailsService;
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import javax.servlet.Filter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
    @Autowired
    private Server server;

    private AuthenticationProvider lazyLdapProvider;


    protected String serviceName() {
        return server.configuration.ldapUnit;
    }
    protected String realmName() {
        return "STOOL";
    }

    protected boolean authEnabled() {
        return server.configuration.auth();
    }

    //--

    @Override
    public void configure(WebSecurity web) {
        if (server.configuration.auth()) {
            /* To allow Pre-flight [OPTIONS] request from browser */
            web.ignoring().antMatchers(HttpMethod.OPTIONS, "/**")
                    .antMatchers("/ui/ressources/**")
                    .antMatchers("/ui/favicon.ico")
                    .antMatchers("/ui/system");
        } else {
            web.ignoring().anyRequest();
        }
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        if (authEnabled()) {
            http.csrf().disable();
            http
                .addFilter(basicAuthenticationFilter())
                .addFilterAfter(new TokenAuthenticationFilter(server.userManager), BasicAuthenticationFilter.class)
                .authenticationProvider(ldapAuthenticationProvider())
                .exceptionHandling()
                    .authenticationEntryPoint(authenticationEntryPoint())
                    .and()
                .authorizeRequests()
                    .antMatchers("/api/**").fullyAuthenticated()
                    .antMatchers("/ui/**").fullyAuthenticated();
        } else {
            http.authorizeRequests().antMatchers("/**").anonymous();
        }
    }

    //--

    @Override
    protected void configure(AuthenticationManagerBuilder auth) {
        auth.authenticationProvider(ldapAuthenticationProvider());
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
        entryPoint.setRealmName(realmName());
        return entryPoint;
    }

    @Bean
    public Filter basicAuthenticationFilter() throws Exception {
        return new BasicAuthenticationFilter(authenticationManager());
    }

    // LDAP
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
    public LdapUserSearch ldapUserSearch() {
        return new FilterBasedLdapUserSearch("ou=users,ou=" + serviceName(), "(uid={0})", contextSource());
    }

    @Bean
    public LdapAuthenticator ldapAuthenticator() {
        BindAuthenticator bindAuthenticator;

        bindAuthenticator = new BindAuthenticator(contextSource());
        bindAuthenticator.setUserSearch(ldapUserSearch());
        return bindAuthenticator;
    }

    @Bean
    public LdapAuthoritiesPopulator ldapAuthoritiesPopulator() {
        DefaultLdapAuthoritiesPopulator authoritiesPopulator;

        authoritiesPopulator = new DefaultLdapAuthoritiesPopulator(contextSource(), "ou=roles,ou=" + serviceName());
        authoritiesPopulator.setGroupSearchFilter("(member=uid={1})");
        authoritiesPopulator.setGroupRoleAttribute("ou");
        authoritiesPopulator.setSearchSubtree(false);
        authoritiesPopulator.setIgnorePartialResultException(true);
        return authoritiesPopulator;
    }

    @Bean
    public AuthenticationProvider ldapAuthenticationProvider() {
        LdapAuthenticationProvider authenticationProvider;
        SimpleAuthorityMapper simpleAuthorityMapper;

        simpleAuthorityMapper = new SimpleAuthorityMapper();
        simpleAuthorityMapper.setDefaultAuthority("ROLE_LOGIN");
        authenticationProvider = new LdapAuthenticationProvider(ldapAuthenticator(), ldapAuthoritiesPopulator());
        authenticationProvider.setAuthoritiesMapper(simpleAuthorityMapper);
        authenticationProvider.setUserDetailsContextMapper(userDetailsContextMapper());
        return authenticationProvider;
    }

    @Bean
    public UserDetailsContextMapper userDetailsContextMapper() {
        return new UserDetailsMapper();
    }

    @Override
    @Bean
    public UserDetailsService userDetailsServiceBean() {
        LdapUserDetailsService result;

        if (server.configuration.auth()) {
            result = new LdapUserDetailsService(ldapUserSearch(), ldapAuthoritiesPopulator());
            result.setUserDetailsMapper(userDetailsContextMapper());
            return result;
        } else {
            return new InMemoryUserDetailsManager();
        }
    }
}
