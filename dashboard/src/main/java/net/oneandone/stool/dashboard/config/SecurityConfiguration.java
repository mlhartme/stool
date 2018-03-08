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
package net.oneandone.stool.dashboard.config;

import net.oneandone.stool.stage.Stage;
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
    @Autowired
    private DashboardProperties properties;

    @Autowired
    private Session session;

    @Autowired
    private Stage self;

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        CasAuthenticationProvider provider;

        provider = new CasAuthenticationProvider();
        provider.setServiceProperties(serviceProperties());
        provider.setTicketValidator(new Cas20ServiceTicketValidator(properties.sso));
        provider.setKey("cas");
        provider.setAuthenticationUserDetailsService(new UserDetailsByNameServiceWrapper(userDetailsService()));

        auth.authenticationProvider(provider);
    }

    @Override
    public void configure(WebSecurity web) {
        web.ignoring().antMatchers("/ressources/**").antMatchers("/favicon.ico").antMatchers("/system");
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        CasAuthenticationFilter filter;
        CasAuthenticationEntryPoint entryPoint;

        filter = new CasAuthenticationFilter();
        filter.setAuthenticationManager(authenticationManager());
        entryPoint = new CasAuthenticationEntryPoint();
        entryPoint.setLoginUrl(properties.sso + "/login/");
        entryPoint.setServiceProperties(serviceProperties());
        http.csrf().disable()
          .exceptionHandling().authenticationEntryPoint(entryPoint)
          .and()
          .addFilter(filter);
        if (properties.sso.isEmpty()) {
            http.authorizeRequests().antMatchers("/**").anonymous();
        } else {
            http.authorizeRequests()
                    .antMatchers("/whoami").fullyAuthenticated()
                    .antMatchers("/stages/").anonymous()
                    .antMatchers("/**").hasRole("LOGIN");
        }
    }

    @Bean
    public ServiceProperties serviceProperties() throws IOException {
        ServiceProperties serviceProperties;

        serviceProperties = new ServiceProperties();
        serviceProperties.setService(self.loadPortsOpt().firstWebapp().httpsUrl(
                session.configuration.vhosts, self.getName(), session.configuration.hostname) + "/j_spring_cas_security_check");
        serviceProperties.setSendRenew(false);
        return serviceProperties;
    }

    @Bean
    public DefaultSpringSecurityContextSource contextSource() {
        DefaultSpringSecurityContextSource contextSource;
        String url;

        url = session.configuration.ldapUrl;
        if (url.isEmpty()) {
            // will never be used - this is just to satisfy parameter checks in the constructor
            url = "ldap://localhost";
        }
        contextSource = new DefaultSpringSecurityContextSource(url);
        contextSource.setUserDn(session.configuration.ldapPrincipal);
        contextSource.setPassword(session.configuration.ldapCredentials);
        return contextSource;
    }

    @Override
    public UserDetailsService userDetailsService() {
        String unit;
        FilterBasedLdapUserSearch userSearch;
        DefaultLdapAuthoritiesPopulator authoritiesPopulator;
        LdapUserDetailsService result;

        unit = session.configuration.ldapUnit;
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
