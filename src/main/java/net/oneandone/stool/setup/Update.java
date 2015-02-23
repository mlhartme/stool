package net.oneandone.stool.setup;

import com.github.zafarkhaja.semver.Version;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.NodeInstantiationException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class Update {
    public static void main(String[] args) throws IOException {
        System.out.println("releases " + list(new World()));
    }
    public static List<FileNode> check(Version current, FileNode home) throws IOException {
        FileNode dest;
        String version;
        Version next;
        FileNode script;
        FileNode download;
        String args;
        List<FileNode> result;

        dest = home.join("downloads");
        result = new ArrayList<>();
        for (Release release : list(home.getWorld())) {
            version = release.name;
            version = Strings.removeLeft(version, "setup-stool-");
            version = Strings.removeRight(version, ".sh");
            next = Version.valueOf(version);
            if (next.greaterThan(current)) {
                script = dest.join("update-stool-" + version + ".sh");
                if (!script.isFile()) {
                    download = dest.join("setup-stool-" + version + ".sh");
                    if (current.getMajorVersion() == next.getMajorVersion() && current.getMinorVersion() == next.getMinorVersion()) {
                        args = home.getAbsolute();
                    } else {
                        args = home.getAbsolute() + " " + home.getParent().join("stool-"
                                + next.getMajorVersion() + "." + next.getMinorVersion()).getAbsolute();
                    }
                    script.writeLines(
                            "#!/bin/sh",
                            "echo Downloading " + release.name + " ...",
                            "curl -L --fail --silent --show-error -o" + download.getAbsolute() + " " + release.url,
                            "chmod a+x " + download.getAbsolute(),
                            download.getAbsolute() + " " + args
                    );
                    script.setPermissions("rwxrwxr-x");
                    result.add(script);
                }
            }
        }
        return result;
    }

    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private static List<Release> list(World world) throws IOException {
        JsonArray releases;
        List<Release> result;
        JsonObject release;
        JsonArray assets;
        JsonObject asset;
        Date date;

        result = new ArrayList<>();
        releases = (JsonArray) new JsonParser().parse(world.validNode("https://api.github.com/repos/mlhartme/stool/releases").readString());
        for (JsonElement element : releases) {
            release = (JsonObject) element;
            assets = (JsonArray) release.get("assets");
            if (assets.size() != 1) {
                throw new IllegalStateException(assets.toString());
            }
            asset = (JsonObject) assets.get(0);
            try {
                date = FMT.parse(asset.get("created_at").getAsString());
            } catch (ParseException e) {
                throw new IOException("invalid date: " + e.getMessage(), e);
            }
            result.add(new Release(asset.get("name").getAsString(), date, asset.get("browser_download_url").getAsString()));
        }
        return result;
    }

    public static class Release {
        public final String name;
        public final Date date;
        public final String url;

        public Release(String name, Date date, String url) {
            this.name = name;
            this.date = date;
            this.url = url;
        }

        public String toString() {
            return name + " " + date + " " + url;
        }
    }
}
