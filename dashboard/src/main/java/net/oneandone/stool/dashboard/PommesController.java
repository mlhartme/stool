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
package net.oneandone.stool.dashboard;

import net.oneandone.stool.Create;
import net.oneandone.sushi.fs.World;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/pommes")
public class PommesController {
    @Autowired
    private World world;

    @RequestMapping("all")
    public List<String> allApplications() throws IOException {
        return Create.pommes(world, "");
    }

    @RequestMapping("{query}")
    public List<String> applicationLookup(@PathVariable(value = "query") String query) throws IOException {
        return Create.pommes(world, query);
    }
}

