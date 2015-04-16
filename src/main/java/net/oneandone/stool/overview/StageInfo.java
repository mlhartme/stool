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
package net.oneandone.stool.overview;


import net.oneandone.stool.configuration.StageConfiguration;
import net.oneandone.stool.configuration.Until;
import net.oneandone.stool.stage.Stage;
import net.oneandone.stool.stage.artifact.Change;
import net.oneandone.stool.stage.artifact.Changes;
import net.oneandone.stool.users.UserNotFound;
import net.oneandone.stool.users.Users;
import net.oneandone.sushi.fs.file.FileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

public class StageInfo {
    private static final Logger LOG = LoggerFactory.getLogger(StageInfo.class);

    public String name;
    public StageConfiguration configuration;
    public String extractionUrl;
    /** Maps name to actual url */
    public Map<String, String> urls;
    public Stage.State running;
    public String owner;
    public boolean updateAvailable;
    public Until until;
    public Changes changes;
    public BuildStats stats;
    public String category;
    public String state;


    public static StageInfo fromStage(FileNode logDir, Stage stage, Users users) throws IOException {
        StageInfo stageInfo;
        stageInfo = new StageInfo();
        stageInfo.name = stage.getName();
        stageInfo.configuration = stage.config();
        stageInfo.extractionUrl = stage.getUrl();
        stageInfo.running = stage.isWorking() ? Stage.State.WORKING : stage.state();
        if (stageInfo.running == Stage.State.UP) {
            stageInfo.urls = stage.urlMap();
        }
        try {
            stageInfo.owner = users.byLogin(stage.owner()).name;
        } catch (NamingException | UserNotFound e) {
            stageInfo.owner = "Unknown";
            LOG.error("Cannot lookup User " + stage.owner(), e);
        }
        stageInfo.updateAvailable = stage.updateAvailable();
        stageInfo.until = stage.config().until;
        Changes changes;

        try {
            changes = stage.changes();
        } catch (IOException e) {
            LOG.error("cannot get changes", e);
            changes = new Changes();
            changes.add(new Change(0, "error", e.getMessage(), System.currentTimeMillis()));
            changes.setException(true);
        }
        if (changes.size() > 0) {
            stageInfo.changes = changes;
        }
        stageInfo.stats = BuildStats.load(logDir, stage);
        if (stageInfo.extractionUrl.contains("/trunk")) {
            stageInfo.category = "trunk";
        } else if (stageInfo.extractionUrl.contains("/branches")) {
            stageInfo.category = "branches";
        } else if (stageInfo.extractionUrl.contains("/workspaces")) { // TODO
            stageInfo.category = "workspaces";
        }

        switch (stageInfo.running) {
            case UP:
                stageInfo.state = "success";
                break;
            case WORKING:
                stageInfo.state = "primary";
                break;
            default:
                stageInfo.state = "danger";
        }

        return stageInfo;
    }


    public Until getUntil() {
        return until;
    }
    public Changes getChanges() {
        return changes;
    }
    public BuildStats getStats() {
        return stats;
    }
    public String getCategory() {
        return category;
    }
    public String getState() {
        return state;
    }
    public String getName() {
        return name;
    }

    public StageConfiguration getConfiguration() {
        return configuration;
    }

    public String getExtractionUrl() {
        return extractionUrl;
    }

    public Map<String, String> getUrls() {
        return urls;
    }

    public String getRunning() {
        return running.name().toLowerCase();
    }

    public String getOwner() {
        return owner;
    }

    public String getShareText() throws UnsupportedEncodingException {
        if (urls == null) {
            return "";
        }
        String content;
        StringBuilder stringBuilder;
        stringBuilder = new StringBuilder("Hi, \n");
        for (String url : urls.values()) {
            stringBuilder.append(url).append("\n");
        }

        content = URLEncoder.encode(stringBuilder.toString(), "UTF-8");
        content = content.replace("+", "%20").replaceAll("\\+", "%20")
          .replaceAll("\\%21", "!")
          .replaceAll("\\%27", "'")
          .replaceAll("\\%28", "(")
          .replaceAll("\\%29", ")")
          .replaceAll("\\%7E", "~");

        return content;
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getHash() {
        return "" + this.toString().hashCode();
    }

    @Override
    public String toString() {
        return "StageInfo{"
          + "name='" + name + '\''
          + ", extractionUrl='" + extractionUrl + '\''
          + ", urls=" + urls
          + ", changes=" + changes
          + ", running=" + running
          + ", owner='" + owner + '\''
          + ", updateAvailable=" + updateAvailable
          + ", category='" + category + '\''
          + ", state='" + state + '\''
          + '}';
    }
}
