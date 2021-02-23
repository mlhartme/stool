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
package net.oneandone.stool.applications;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import net.oneandone.inline.ArgumentException;
import net.oneandone.stool.core.Configuration;
import net.oneandone.stool.core.Dependencies;
import net.oneandone.stool.util.Expire;
import net.oneandone.stool.util.Json;
import net.oneandone.sushi.fs.file.FileNode;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class Application {
    public static final String HELM_APPLICATION = "helmApplication";

    /** loads the class ex- or implicitly definded by a chart */
    public static Application loadChartApplication(ObjectMapper yaml, String name, FileNode chart) throws IOException {
        Application result;
        ObjectNode loaded;
        ObjectNode applicationValue;
        FileNode tagFile;

        try (Reader src = chart.join("values.yaml").newReader()) {
            loaded = (ObjectNode) yaml.readTree(src);
        }
        tagFile = Helm.tagFile(chart);
        result = new Application("TODO", "TODO", name, name, tagFile.exists() ? tagFile.readString().trim() : "unknown");
        applicationValue = (ObjectNode) loaded.remove("application");
        result.defineBaseAll(loaded.fields());
        if (applicationValue != null) {
            result.defineAll(applicationValue.fields());
        }
        return result;
    }

    /** from inline, label or classes; always extends */
    public static Application loadLiteral(Map<String, Application> existing, String origin, String author, ObjectNode application) throws IOException {
        String extendz;
        Application base;
        Application derived;
        String name;

        name = Json.string(application, "name");
        extendz = Json.string(application, "extends");
        base = existing.get(extendz);
        if (base == null) {
            throw new IOException("application not found: " + extendz);
        }
        derived = base.derive(origin, author, name);
        derived.defineAll(application.get("properties").fields());
        return derived;
    }

    public static Application loadHelm(ObjectNode application) {
        Application result;
        String name;

        name = Json.string(application, "name");
        result = new Application(Json.stringOpt(application, "origin"), Json.stringOpt(application, "author"), name, Json.string(application, "chart"),
                Json.string(application, "chartVersion"));
        result.defineBaseAll(application.get("properties").fields());
        return result;
    }

    public static Application forTest(String name, String... nameValues) {
        Application result;

        result = new Application("synthetic", null, name, "unusedChart", "noVersion");
        for (int i = 0; i < nameValues.length; i += 2) {
            result.defineBase(new Property(nameValues[i], nameValues[i + 1]));
        }
        return result;
    }

    //--

    // metadata
    public final String origin;
    public final String author; // null (from file), LIB, or image author

    public final String name;
    public final String chart;
    public final String chartVersion;
    public final Map<String, Property> properties;

    private Application(String origin, String author, String name, String chart, String chartVersion) {
        this.origin = origin;
        this.author = author;
        this.name = name;
        this.chart = chart;
        this.chartVersion = chartVersion;
        this.properties = new LinkedHashMap<>();
    }

    public void defineBaseAll(Iterator<Map.Entry<String, JsonNode>> iter) {
        Map.Entry<String, JsonNode> entry;

        while (iter.hasNext()) {
            entry = iter.next();
            defineBase(Property.forYaml(entry.getKey(), entry.getValue()));
        }
    }
    public void defineBase(Property value) {
        if (properties.put(value.name, value) != null) {
            throw new IllegalStateException(value.name);
        }
    }

    public void defineAll(Iterator<Map.Entry<String, JsonNode>> iter) {
        Map.Entry<String, JsonNode> entry;
        String key;

        while (iter.hasNext()) {
            entry = iter.next();
            key = entry.getKey();
            define(Property.forYaml(key, entry.getValue()));
        }
    }

    public void define(Property property) {
        Property old;

        old = properties.get(property.name);
        if (old != null) {
            if (old.privt) {
                throw new IllegalStateException("you cannot override private property: " + property.name);
            }
            if (property.extra) {
                throw new IllegalStateException("extra property overrides base property: " + property.name);
            }
            if (old.doc != null && property.doc == null) {
                property = property.withDoc(old.doc);
            }
            if (!old.value.isEmpty() && property.value.isEmpty()) {
                property = property.withValue(old.value);
            }
        } else {
            if (!property.extra) {
                throw new IllegalStateException("extra value expected: " + property.name);
            }
        }
        properties.put(property.name, property);
    }

    public void setValues(Map<String, String> clientValues) {
        String key;
        Property old;

        for (Map.Entry<String, String> entry : clientValues.entrySet()) {
            key = entry.getKey();
            old = properties.get(key);
            if (old == null) {
                throw new ArgumentException("unknown value: " + key);
            }
            properties.put(key, new Property(key, old.privt, false, old.doc, entry.getValue()));
        }
    }

    public int size() {
        return properties.size();
    }

    public Property get(String value) {
        Property result;

        result = properties.get(value);
        if (result == null) {
            throw new IllegalStateException(value);
        }
        return result;
    }

    public ObjectNode toObject(ObjectMapper yaml) {
        ObjectNode node;
        ObjectNode p;

        node = yaml.createObjectNode();
        node.set("origin", new TextNode(origin)); // TODO: saved only, not loaded
        if (author != null) {
            node.set("author", new TextNode(author)); // TODO: saved only, not loaded
        }
        node.set("name", new TextNode(name));
        node.set("chart", new TextNode(chart));
        node.set("chartVersion", new TextNode(chartVersion));
        p = yaml.createObjectNode();
        node.set("properties", p);
        for (Property property : properties.values()) {
            p.set(property.name, property.toObject(yaml));
        }
        return node;
    }

    public Application derive(String derivedOrigin, String derivedAuthor, String withName) {
        Application result;

        result = new Application(derivedOrigin, derivedAuthor, withName, chart, chartVersion);
        for (Property property : properties.values()) {
            result.defineBase(property);
        }
        return result;
    }

    public FileNode createValuesFile(Configuration configuration, Map<String, String> actuals) throws IOException {
        ObjectNode dest;
        Expire expire;
        FileNode file;

        dest = configuration.yaml.createObjectNode();
        for (Map.Entry<String, String> entry : actuals.entrySet()) {
            dest.put(entry.getKey(), entry.getValue());
        }

        dest.set(HELM_APPLICATION, toObject(configuration.yaml));

        // normalize expire
        expire = Expire.fromHuman(Json.string(dest, Dependencies.VALUE_EXPIRE, Expire.fromNumber(configuration.defaultExpire).toString()));
        if (expire.isExpired()) {
            throw new ArgumentException("stage expired: " + expire);
        }
        dest.put(Dependencies.VALUE_EXPIRE, expire.toString());

        file = configuration.world.getTemp().createTempFile().writeString(dest.toPrettyString());
        return file;
    }
}
