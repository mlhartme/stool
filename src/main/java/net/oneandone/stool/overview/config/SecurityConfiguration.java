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
package net.oneandone.stool.overview.config;

import net.oneandone.stool.util.Session;
import org.jasig.cas.client.validation.Cas20ServiceTicketValidator;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.security.core.userdetails.UserDetailsByNameServiceWrapper;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.InetOrgPersonContextMapper;
import org.springframework.security.ldap.userdetails.LdapUserDetailsService;

import java.io.IOException;

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
        web.ignoring().antMatchers("/ressources/**").antMatchers("/favicon.ico").antMatchers("/system");
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        CasAuthenticationFilter filter;

        filter = new CasAuthenticationFilter();
        filter.setAuthenticationManager(authenticationManager());
        http.csrf().disable()
          .authenticationProvider(casAuthenticationProvider())
          .exceptionHandling().authenticationEntryPoint(casAuthenticationEntryPoint())
          .and()
          .addFilter(filter)
          .authorizeRequests()
          .antMatchers("/whoami").fullyAuthenticated()
          .antMatchers("/**").hasRole("LOGIN");
    }

    private String hostname() throws IOException {
        return "https://" + session.stoolConfiguration.hostname + ":" + session.load("overview").config().ports.tomcatHttps() + "/";
    }

    @Bean
    public ServiceProperties serviceProperties() throws IOException {
        ServiceProperties serviceProperties;
        serviceProperties = new ServiceProperties();
        serviceProperties.setService(hostname() + "/j_spring_cas_security_check");
        serviceProperties.setSendRenew(false);
        return serviceProperties;
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
    public CasAuthenticationEntryPoint casAuthenticationEntryPoint() throws IOException {
        CasAuthenticationEntryPoint casAuthenticationEntryPoint;
        casAuthenticationEntryPoint = new CasAuthenticationEntryPoint();
        casAuthenticationEntryPoint.setLoginUrl(LOGIN_URL);
        casAuthenticationEntryPoint.setServiceProperties(serviceProperties());
        return casAuthenticationEntryPoint;
    }

    @Autowired
    private Session session;

    @Bean
    public DefaultSpringSecurityContextSource contextSource() {
        DefaultSpringSecurityContextSource contextSource;

        contextSource = new DefaultSpringSecurityContextSource(PROVIDER_URL);
        contextSource.setUserDn(USERDN);
        contextSource.setPassword(session.stoolConfiguration.authenticationPassword);
        return contextSource;
    }

    @Override
    @Bean
    public UserDetailsService userDetailsServiceBean() throws Exception {
        FilterBasedLdapUserSearch userSearch;
        DefaultLdapAuthoritiesPopulator authoritiesPopulator;
        LdapUserDetailsService result;

        userSearch = new FilterBasedLdapUserSearch("ou=cisostages", "(uid={0})", contextSource());
        authoritiesPopulator = new DefaultLdapAuthoritiesPopulator(contextSource(), "ou=roles,ou=cisostages");
        authoritiesPopulator.setGroupSearchFilter("(member=uid={1})");
        authoritiesPopulator.setGroupRoleAttribute("ou");
        authoritiesPopulator.setSearchSubtree(false);
        authoritiesPopulator.setIgnorePartialResultException(true);

        result = new LdapUserDetailsService(userSearch, authoritiesPopulator);
        result.setUserDetailsMapper(new InetOrgPersonContextMapper());
        return result;
    }

}
