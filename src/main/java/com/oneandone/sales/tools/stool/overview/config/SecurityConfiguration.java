/**
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
package com.oneandone.sales.tools.stool.overview.config;

import org.jasig.cas.client.validation.Cas20ServiceTicketValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.InetOrgPersonContextMapper;
import org.springframework.security.ldap.userdetails.LdapUserDetailsService;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.Query;
import java.lang.management.ManagementFactory;
import java.util.Set;

@EnableWebSecurity
@Configuration
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {
    private static final String BASEDN = "ou=ims_service,o=1und1,c=DE";
    private static final String PROVIDER_URL = "ldaps://ldap.1and1.org:636/" + BASEDN;
    private static final String USERDN = "uid=cisostages,ou=accounts,ou=ims_service,o=1und1,c=DE";
    private static final String URL_PREFIX = "https://login.1and1.org/ims-sso";
    private static final String LOGIN_URL = URL_PREFIX + "/login/";

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.authenticationProvider(casAuthenticationProvider());
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers("/ressources/**").antMatchers("/favicon.ico").antMatchers("/system").antMatchers("/monitoring");
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
          .authenticationProvider(casAuthenticationProvider())
          .exceptionHandling().authenticationEntryPoint(casAuthenticationEntryPoint())
          .and()
          .addFilter(casAuthenticationFilter())
          .authorizeRequests()
          .antMatchers("/whoami").fullyAuthenticated()
          .antMatchers("/**").hasRole("LOGIN");
    }

    @Bean
    public String hostname() {
        StringBuilder url;
        url = new StringBuilder();
        String stoolUrl = System.getProperty("stool.url");
        if (stoolUrl != null) {
            url.append(stoolUrl);
            return url.toString();
        }
        try {
            MBeanServer mBeanServer;
            Set<ObjectName> hosts;
            Set<ObjectName> ports;

            mBeanServer = ManagementFactory.getPlatformMBeanServer();
            hosts = mBeanServer.queryNames(new ObjectName("*:type=Host,*"), null);
            ports = mBeanServer.queryNames(new ObjectName("*:type=Connector,*"),
              Query.match(Query.attr("scheme"), Query.value("https")));
            if (hosts.size() == 1 && ports.size() == 1) {
                url.append("https://");
                url.append(hosts.iterator().next().getKeyProperty("host"));
                url.append(":").append(ports.iterator().next().getKeyProperty("port"));
                url.append("/");
                return url.toString();
            }

        } catch (MalformedObjectNameException e) {
            //empty
        }
        throw new RuntimeException("Cannot determine hostname. Use -Dstool.url to force a url");
    }

    @Bean
    public ServiceProperties serviceProperties() {
        ServiceProperties serviceProperties;
        serviceProperties = new ServiceProperties();
        serviceProperties.setService(hostname() + "/j_spring_cas_security_check");
        serviceProperties.setSendRenew(false);
        return serviceProperties;
    }

    @Bean
    public CasAuthenticationFilter casAuthenticationFilter() throws Exception {
        CasAuthenticationFilter casAuthenticationFilter;
        casAuthenticationFilter = new CasAuthenticationFilter();
        casAuthenticationFilter.setAuthenticationManager(authenticationManager());
        return casAuthenticationFilter;
    }

    @Bean
    public CasAuthenticationProvider casAuthenticationProvider() throws Exception {
        CasAuthenticationProvider casAuthenticationProvider;
        casAuthenticationProvider = new CasAuthenticationProvider();
        casAuthenticationProvider.setServiceProperties(serviceProperties());
        casAuthenticationProvider.setTicketValidator(ticketValidator());
        casAuthenticationProvider.setKey("cas");
        casAuthenticationProvider.setAuthenticationUserDetailsService(new UserDetailsByNameServiceWrapper(userDetailsServiceBean()));
        return casAuthenticationProvider;
    }

    @Bean
    public Cas20ServiceTicketValidator ticketValidator() {
        return new Cas20ServiceTicketValidator(URL_PREFIX);
    }

    @Bean
    public CasAuthenticationEntryPoint casAuthenticationEntryPoint() {
        CasAuthenticationEntryPoint casAuthenticationEntryPoint;
        casAuthenticationEntryPoint = new CasAuthenticationEntryPoint();
        casAuthenticationEntryPoint.setLoginUrl(LOGIN_URL);
        casAuthenticationEntryPoint.setServiceProperties(serviceProperties());
        return casAuthenticationEntryPoint;
    }

    @Bean
    public DefaultSpringSecurityContextSource contextSource() {
        DefaultSpringSecurityContextSource contextSource;
        contextSource = new DefaultSpringSecurityContextSource(PROVIDER_URL);
        contextSource.setUserDn(USERDN);
        contextSource.setPassword(System.getProperty("overview.password"));
        return contextSource;
    }

    @Bean
    public FilterBasedLdapUserSearch ldapUserSearch() {
        FilterBasedLdapUserSearch filterBasedLdapUserSearch;
        filterBasedLdapUserSearch = new FilterBasedLdapUserSearch("ou=cisostages", "(uid={0})", contextSource());
        return filterBasedLdapUserSearch;
    }

    @Bean
    public BindAuthenticator bindAuthenticator() {
        BindAuthenticator bindAuthenticator;
        bindAuthenticator = new BindAuthenticator(contextSource());
        bindAuthenticator.setUserSearch(ldapUserSearch());
        return bindAuthenticator;
    }

    @Bean
    public DefaultLdapAuthoritiesPopulator ldapAuthoritiesPopulator() {
        DefaultLdapAuthoritiesPopulator authoritiesPopulator;
        authoritiesPopulator = new DefaultLdapAuthoritiesPopulator(contextSource(), "ou=roles,ou=cisostages");
        authoritiesPopulator.setGroupSearchFilter("(member=uid={1})");
        authoritiesPopulator.setGroupRoleAttribute("ou");
        authoritiesPopulator.setSearchSubtree(false);
        authoritiesPopulator.setIgnorePartialResultException(true);
        return authoritiesPopulator;
    }

    @Bean
    public LdapAuthenticationProvider ldapAuthenticationProvider() {
        LdapAuthenticationProvider authenticationProvider;
        SimpleAuthorityMapper simpleAuthorityMapper = new SimpleAuthorityMapper();
        authenticationProvider = new LdapAuthenticationProvider(bindAuthenticator(), ldapAuthoritiesPopulator());
        authenticationProvider.setAuthoritiesMapper(simpleAuthorityMapper);
        return authenticationProvider;
    }

    @Override
    @Bean
    public UserDetailsService userDetailsServiceBean() throws Exception {
        LdapUserDetailsService ldapUserDetailsService = new LdapUserDetailsService(ldapUserSearch(), ldapAuthoritiesPopulator());
        ldapUserDetailsService.setUserDetailsMapper(new InetOrgPersonContextMapper());
        return ldapUserDetailsService;
    }

}
