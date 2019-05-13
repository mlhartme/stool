package net.oneandone.stool.server.users;

import net.oneandone.stool.server.Server;
import org.jasig.cas.client.validation.Cas20ServiceTicketValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.cas.ServiceProperties;
import org.springframework.security.cas.authentication.CasAuthenticationProvider;
import org.springframework.security.cas.web.CasAuthenticationEntryPoint;
import org.springframework.security.cas.web.CasAuthenticationFilter;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.core.userdetails.UserDetailsByNameServiceWrapper;
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

/**
 * Here's an overview that helped me get started with Spring security:
 * https://springbootdev.com/2017/08/23/spring-security-authentication-architecture/
 */
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
                .addFilter(casAuthenticationFilter())
                .exceptionHandling()
                    .authenticationEntryPoint(casAuthenticationEntryPoint())
                    .and()

                .authenticationProvider(ldapAuthenticationProvider())
                .authenticationProvider(casAuthenticationProvider())

                .authorizeRequests()
                    .antMatchers("/api/**").fullyAuthenticated()
                    .antMatchers("/ui/**").fullyAuthenticated();
        } else {
            http.authorizeRequests().antMatchers("/**").anonymous();
        }
    }

    //-- basic authentication against ldap

    @Bean
    public Filter basicAuthenticationFilter() throws Exception {
        return new BasicAuthenticationFilter(authenticationManager());
    }

    @Bean
    public DefaultSpringSecurityContextSource ldapContextSource() {
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
        return new FilterBasedLdapUserSearch("ou=users,ou=" + serviceName(), "(uid={0})", ldapContextSource());
    }

    @Bean
    public LdapAuthenticator ldapAuthenticator() {
        BindAuthenticator bindAuthenticator;

        bindAuthenticator = new BindAuthenticator(ldapContextSource());
        bindAuthenticator.setUserSearch(ldapUserSearch());
        return bindAuthenticator;
    }

    @Bean
    public LdapAuthoritiesPopulator ldapAuthoritiesPopulator() {
        DefaultLdapAuthoritiesPopulator authoritiesPopulator;

        authoritiesPopulator = new DefaultLdapAuthoritiesPopulator(ldapContextSource(), "ou=roles,ou=" + serviceName());
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

    //-- cas

    private CasAuthenticationProvider casAuthenticationProvider() {
        CasAuthenticationProvider provider;

        provider = new CasAuthenticationProvider();
        provider.setServiceProperties(serviceProperties());
        provider.setTicketValidator(new Cas20ServiceTicketValidator(server.configuration.ldapSso));
        provider.setKey("cas");
        provider.setAuthenticationUserDetailsService(new UserDetailsByNameServiceWrapper(userDetailsService()));

        return provider;
    }

    private CasAuthenticationFilter casAuthenticationFilter() throws Exception {
        CasAuthenticationFilter filter;

        filter = new CasAuthenticationFilter();
        filter.setAuthenticationManager(authenticationManager());
        return filter;
    }

    private CasAuthenticationEntryPoint casAuthenticationEntryPoint() {
        CasAuthenticationEntryPoint entryPoint;

        entryPoint = new CasAuthenticationEntryPoint();
        entryPoint.setLoginUrl(server.configuration.ldapSso + "/login/");
        entryPoint.setServiceProperties(serviceProperties());
        return entryPoint;
    }

    @Bean
    public ServiceProperties serviceProperties() {
        ServiceProperties serviceProperties;

        serviceProperties = new ServiceProperties();
        // TODO: report an error when not running https ...
        serviceProperties.setService("https://" + server.configuration.dockerHost + ":" + server.configuration.portFirst + "/j_spring_cas_security_check");
        serviceProperties.setSendRenew(false);
        return serviceProperties;
    }
}
