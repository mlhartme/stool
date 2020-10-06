/*
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.stool.server.users;

import net.oneandone.stool.server.Server;
import org.jasig.cas.client.validation.Cas20ServiceTicketValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.servlet.Filter;
import java.util.LinkedHashMap;

/**
 * Here's an overview that helped me get started with Spring security:
 * https://springbootdev.com/2017/08/23/spring-security-authentication-architecture/
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
    @Autowired
    private Server server;

    protected boolean enabled() {
        return server.configuration.auth();
    }

    //--

    @Override
    public void configure(WebSecurity web) {
        if (server.configuration.auth()) {
            /* To allow Pre-flight [OPTIONS] request from browser */
            web.ignoring().antMatchers(HttpMethod.OPTIONS, "/**");
        } else {
            web.ignoring().anyRequest();
        }
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        if (enabled()) {
            // Stool server doesn't need session, but I cannot disable them here for security because cas relies on it
            http.csrf().disable();
            http
                .addFilter(basicAuthenticationFilter())
                .headers()
                    .httpStrictTransportSecurity().disable()  // because sub-domains (or different ports might include http links
                    .and()
                .addFilterAfter(new TokenAuthenticationFilter(server.userManager), BasicAuthenticationFilter.class)
                .addFilter(casAuthenticationFilter())
                .exceptionHandling()
                    .authenticationEntryPoint(entryPoints())
                    .and()

                .authorizeRequests()
                    .antMatchers("/webjars/**").permitAll()
                    .anyRequest().authenticated();
        } else {
            http.authorizeRequests().antMatchers("/**").anonymous();
        }
    }

    /** don't try cas for /api/** */
    private AuthenticationEntryPoint entryPoints() {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> map;

        map = new LinkedHashMap<>();
        map.put(new AntPathRequestMatcher("/api/**"), new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        map.put(new AntPathRequestMatcher("/**"), casAuthenticationEntryPoint());
        return new DelegatingAuthenticationEntryPoint(map);
    }

    @Override // note that moving this into configure(http) doesn't work ...
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.authenticationProvider(ldapAuthenticationProvider());
        auth.authenticationProvider(casAuthenticationProvider());
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

        url = enabled() ? server.configuration.ldapUrl : "ldap://will-no-be-used";
        contextSource = new DefaultSpringSecurityContextSource(url);
        contextSource.setUserDn(server.configuration.ldapPrincipal);
        contextSource.setPassword(server.configuration.ldapCredentials);
        return contextSource;
    }

    @Bean
    public LdapUserSearch ldapUserSearch() {
        return new FilterBasedLdapUserSearch("ou=users,ou=" + server.configuration.ldapUnit, "(uid={0})", ldapContextSource());
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

        authoritiesPopulator = new DefaultLdapAuthoritiesPopulator(ldapContextSource(), "ou=roles,ou=" + server.configuration.ldapUnit);
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

        if (enabled()) {
            result = new LdapUserDetailsService(ldapUserSearch(), ldapAuthoritiesPopulator());
            result.setUserDetailsMapper(userDetailsContextMapper());
            return result;
        } else {
            return new InMemoryUserDetailsManager();
        }
    }

    //-- cas authentication

    @Bean
    public CasAuthenticationProvider casAuthenticationProvider() {
        CasAuthenticationProvider provider;

        provider = new CasAuthenticationProvider();
        provider.setServiceProperties(serviceProperties());
        provider.setTicketValidator(new Cas20ServiceTicketValidator(server.configuration.ldapSso));
        provider.setKey("cas");
        provider.setAuthenticationUserDetailsService(new UserDetailsByNameServiceWrapper(userDetailsServiceBean()));
        return provider;
    }

    private CasAuthenticationFilter casAuthenticationFilter() throws Exception {
        CasAuthenticationFilter filter;

        filter = new CasAuthenticationFilter();
        filter.setServiceProperties(serviceProperties());
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
        String protocol;
        String url;

        serviceProperties = new ServiceProperties();
        // TODO: report an error when not running https ...
        protocol = System.getProperty("security.require-ssl") != null ? "https" : "http";
        url = protocol + "://" + server.configuration.fqdn + "/login/cas";
        Server.LOGGER.info("sso service: " + url);
        serviceProperties.setService(url);
        serviceProperties.setSendRenew(false);
        return serviceProperties;
    }
}
