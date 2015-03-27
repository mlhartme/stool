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

import net.oneandone.pommes.cli.Environment;
import net.oneandone.pommes.model.Database;
import net.oneandone.pommes.model.Pom;
import net.oneandone.sushi.fs.World;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.WildcardQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

@RestController
@RequestMapping("/pommes")
public class PommesController {

    @Autowired
    private World world;

    public static Query or(String querystring) {
        return new WildcardQuery(new Term("origin", querystring));
    }

    @RequestMapping("all")
    public List<Pom> allApplications() throws IOException, QueryNodeException, URISyntaxException {
        return database().query("", new Environment(world));
    }
    @RequestMapping("{query}")
    public List<Pom> applicationLookup(@PathVariable(value = "query") String querystring)
      throws IOException, QueryNodeException, URISyntaxException {

        BooleanQuery appQuery;

        appQuery = new BooleanQuery();

        appQuery.add(or("*apps*" + querystring + "*"), BooleanClause.Occur.SHOULD);
        appQuery.add(or("*dsl*" + querystring + "*"), BooleanClause.Occur.SHOULD);
        appQuery.add(or("*cart*"), BooleanClause.Occur.MUST_NOT);


        return database().query(appQuery);
    }


    public Database database() throws URISyntaxException, IOException {
        Database database;

        database = Database.load(world);
        database.downloadOpt();
        return database;
    }

}

