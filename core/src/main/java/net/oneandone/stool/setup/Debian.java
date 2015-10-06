package net.oneandone.stool.setup;

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.launcher.ExitCode;

import java.io.IOException;

public class Debian {
    public static void postinst(String ... args) throws IOException {
        World world;
        FileNode cwd;
        String user;
        String bin;
        String home;
        String group;

        world = new World();
        cwd = (FileNode) world.getWorking();
        user = "stool";
        bin= "/usr/share/stool"
        // TODO replace this check by some kind of configuration
        if (world.file("/opt/ui/opt/tools").isDirectory()) {
            home = "/opt/ui/opt/tools/stool";
            group = "users";
        } else {
            home = "/var/lib/stool";
            group = "stool";
        }

        if (args.length > 0 && args[1].equals("configure")) {
            System.out.println("configure home " + home + ", group " + group);
            if (execBoolean(cwd, "getent", "group", group)) {
                System.out.println("re-using existing group: " + group);
            } else {
                System.out.println("creating group: " + group);
                cwd.exec("groupadd", group);
                for (FileNode file : world.file("/home/").list()) {
                    String name = file.getName();
                    if (execBoolean(cwd, "id", "-u", name)) {
                        echo("add user to group: ${name}");
                        cwd.exec("usermod", "-a", "-G", group, name);
                    } else {
                        echo("ignoring home directory: $file");
                    }
                }
            }
        }


    if id -u ${user} >/dev/null 2>&1; then
        echo "re-using existing user: ${user}"
    else
        echo "creating user: ${user}"
        if [ -d /home/${user} ] ; then
             echo "home directory for user ${user} already exists"
             exit 1
        fi
        adduser --system --ingroup ${group} --home /home/${user} ${user}
    fi
    if groups ${user} | cut --delimiter=: --fields=2 | grep -q -w ${group} ; then
        echo "user ${user} already in group ${group}"
    else
        echo "add user ${user} to group ${group}"
        usermod -a -G ${group} ${user}
    fi
    ln -s ${home} ${bin}/home

    # "sg" is used to set the proper group if home is newly generated
    STOOL_BIN=${bin} sg ${group} "java -cp ${bin}/stool.jar net.oneandone.stool.setup.DebianHome ${home}"
    sudo -u ${user} ${bin}/stool-raw.sh chown -stage dashboard

    update-rc.d stool defaults
fi

    }

    private static void echo(String str) {
        System.out.println(str);
    }

    private static boolean execBoolean(FileNode cwd, String ... args) throws IOException {
        try {
            cwd.exec(args);
            return true;
        } catch (ExitCode e) {
            return false;
        }
    }
}
