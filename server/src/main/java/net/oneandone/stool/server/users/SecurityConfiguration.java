package net.oneandone.stool.server.users;

import net.oneandone.stool.server.Server;
import org.jasig.cas.client.validation.Cas20ServiceTicketValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.cas.ServiceProperties;
import org.springframework.security.cas.authentication.CasAuthenticationProvider;
import org.springframework.security.cas.web.CasAuthenticationEntryPoint;
import org.springframework.security.cas.web.CasAuthenticationFilter;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsByNameServiceWrapper;
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
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        CasAuthenticationProvider provider;

        provider = new CasAuthenticationProvider();
        provider.setServiceProperties(serviceProperties());
        provider.setTicketValidator(new Cas20ServiceTicketValidator(server.configuration.ldapSso));
        provider.setKey("cas");
        provider.setAuthenticationUserDetailsService(new UserDetailsByNameServiceWrapper(userDetailsService()));

        auth.authenticationProvider(provider);
    }

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
        CasAuthenticationFilter filter;
        CasAuthenticationEntryPoint entryPoint;

        filter = new CasAuthenticationFilter();
        filter.setAuthenticationManager(authenticationManager());
        entryPoint = new CasAuthenticationEntryPoint();
        entryPoint.setLoginUrl(server.configuration.ldapSso + "/login/");
        entryPoint.setServiceProperties(serviceProperties());
        http.csrf().disable()
                .exceptionHandling().authenticationEntryPoint(entryPoint)
                .and()
                .addFilter(filter);

        if (server.configuration.auth()) {
            http
               .addFilterAfter(new TokenAuthenticationFilter(server.userManager), BasicAuthenticationFilter.class)
               .sessionManagement()
                  .disable()
               .csrf().disable() // no sessions -> no need to protect them with csrf
               .authorizeRequests()
                    .antMatchers("/api/**").fullyAuthenticated()
                    .antMatchers("/ui/whoami").fullyAuthenticated()
                    .antMatchers("/ui/stages/").anonymous()
                    .antMatchers("/ui/**").hasRole("LOGIN")
                    .and()
               .httpBasic().realmName(REALM);
        } else {
            // disabled security
            http.authorizeRequests().antMatchers("/**").anonymous();
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
        String unit;
        FilterBasedLdapUserSearch userSearch;
        DefaultLdapAuthoritiesPopulator authoritiesPopulator;
        LdapUserDetailsService result;

        if (!server.configuration.auth()) {
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

    @Bean
    public ServiceProperties serviceProperties() {
        ServiceProperties serviceProperties;

        serviceProperties = new ServiceProperties();
        // TODO: http or https
        serviceProperties.setService("http://" + server.configuration.dockerHost + ":" + server.configuration.portFirst + "/j_spring_cas_security_check");
        serviceProperties.setSendRenew(false);
        return serviceProperties;
    }
}
