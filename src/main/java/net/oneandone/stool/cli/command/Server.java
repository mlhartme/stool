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
package net.oneandone.stool.cli.command;

import net.oneandone.stool.cli.Globals;
import net.oneandone.stool.Main;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class Server extends ClientCommand {
    public Server(Globals globals) {
        super(globals);
    }

    public void run() throws IOException, InterruptedException {
        ConfigurableApplicationContext context;
        final CountDownLatch closed;

        closed = new CountDownLatch(1);
        context = SpringApplication.run(Main.class, new String[] {});
        context.addApplicationListener(new ApplicationListener<ApplicationEvent>() {
            @Override
            public void onApplicationEvent(ApplicationEvent event) {
                if (event instanceof ContextClosedEvent) {
                    closed.countDown();
                }
            }
        });
        closed.await();
        System.out.println("terminating ...");
    }
}
