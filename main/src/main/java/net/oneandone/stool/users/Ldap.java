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
package net.oneandone.stool.users;

import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ldap access and factory for users.
 *
 * Note: I could Spring Ldap stuff for this, but I didn't succeed to intitiate these objects without injection.
 */
public class Ldap {
    public static Ldap create(String url, String principal, String credentials, String context) {
        Hashtable<String, String> env;

        env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, url);

        if (principal != null) {
            // Authentication: http://docs.oracle.com/javase/jndi/tutorial/ldap/security/ldap.html
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, principal);
            env.put(Context.SECURITY_CREDENTIALS, credentials);
        }
        return new Ldap(context, env);
    }

    private final String context;
    private final Hashtable environment;
    private DirContext lazyContext;

    private Ldap(String context, Hashtable environment) {
        this.context = context;
        this.environment = environment;
        this.lazyContext = null;
    }

    //-- lookup users

    /** @return never null */
    public User lookup(String login) throws NamingException, UserNotFound {
        Map<String, User> result;

        result = listFromLdap("uid", login);
        switch (result.size()) {
            case 0:
                throw new UserNotFound(login);
            case 1:
                return result.values().iterator().next();
            default:
                throw new IllegalStateException(login + ": ambiguous");
        }
    }

    /** @return uid to user mapping */
    private LinkedHashMap<String, User> listFromLdap(String ... args) throws NamingException {
        LinkedHashMap<String, User> result;
        NamingEnumeration<SearchResult> answer;
        BasicAttributes matchAttrs;

        if (args.length % 2 != 0) {
            throw new IllegalArgumentException();
        }
        matchAttrs = new BasicAttributes(true); // ignore attribute name case
        for (int i = 0; i < args.length; i+= 2) {
            matchAttrs.put(new BasicAttribute(args[i], args[i + 1]));
        }
        answer = search(context, matchAttrs);
        result = new LinkedHashMap<>();
        while (answer.hasMore()) {
            SearchResult obj = answer.next();
            Attributes attrs = obj.getAttributes();
            result.put(get(attrs, "uid"),
                    new User(
                            get(attrs, "uid"),
                            get(attrs, "givenname") + " " + get(attrs, "sn"),
                            get(attrs, "mail") /* email is mandatory for me - although ldap returns employees without */));
        }
        return result;
    }

    private String get(Attributes attrs, String name) throws NamingException {
        String result;

        result = getOpt(attrs, name, null);
        if (result == null) {
            throw new IllegalStateException("attribute not found: " + name);
        }
        return result;
    }

    private String getOpt(Attributes attrs, String name, String dflt) throws NamingException {
        String result;
        Attribute attribute;
        NamingEnumeration e;

        attribute = attrs.get(name);
        if (attribute == null) {
            return dflt;
        }
        e = attribute.getAll();
        result = e.next().toString();
        if (e.hasMore()) {
            throw new IllegalStateException(name);
        }
        return result;
    }

    private NamingEnumeration<SearchResult> search(String name, Attributes matchAttrs) throws NamingException {
        try {
            return searchWithoutRetry(name, matchAttrs);
        } catch (CommunicationException first) {
            // I've seen ldap queries fail occasionally with a CommunicationException "connection closed". So try it twice.
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // fall-through
            }
            try {
                return searchWithoutRetry(name, matchAttrs);
            } catch (NamingException second) {
                second.addSuppressed(first);
                throw second;
            }
        }
    }

    private NamingEnumeration<SearchResult> searchWithoutRetry(String name, Attributes matchAttrs) throws NamingException {
        if (lazyContext == null) {
            // caution: creating this context is expensive
            lazyContext = new InitialLdapContext(environment, null);
        }
        try {
            return lazyContext.search(name, matchAttrs);
        } catch (NamingException e) {
            // reset LdapContext, the connection is broken
            lazyContext = null;
            throw e;
        }
    }
}
