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

import net.oneandone.sushi.fs.ExistsException;
import net.oneandone.sushi.fs.MkdirException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.ListIterator;

@RestController
@RequestMapping("/processes")
public class ProcessesController {

    @Autowired
    private FileNode logs;

    @RequestMapping(value = "{id}", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity state(@PathVariable(value = "id") String id) throws IOException {
        Node stat;
        String stoolProcess;

        stat = logs.join(id + ".stat");
        if (!stat.exists()) {
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        }

        stoolProcess = stat.readString();
        return new ResponseEntity(stoolProcess, HttpStatus.OK);

    }

    @RequestMapping(value = "{id}/log", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity log(@PathVariable(value = "id") String id, @RequestParam(defaultValue = "0") Integer index)
      throws IOException, InterruptedException {
        Node logfile;
        StringBuilder output;
        MultiValueMap<String, String> headers;
        output = new StringBuilder();
        List<String> strings;
        ListIterator<String> iterator;

        headers = new HttpHeaders();

        try {
            logfile = logFile(id);
        } catch (ResourceNotFoundException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        strings = logfile.readLines();
        iterator = strings.listIterator(index);
        while (iterator.hasNext()) {
            output.append(iterator.next()).append("<br />");
        }
        headers.set("X-index", "" + strings.size());
        if (logs.join(id + ".running").exists()) {
            headers.set("X-Running", "true");
        }
        return new ResponseEntity<>(output.toString(), headers, HttpStatus.OK);
    }

    public Node logFile(String id) throws InterruptedException, ExistsException, MkdirException {
        Node logfile;

        logfile = logs.join(id + ".log");
        int i = 0;
        do {
            if (logfile.exists()) {
                return logfile;
            } else {
                Thread.sleep(500);
            }
            i++;
        } while (i <= 20);
        throw new ResourceNotFoundException();
    }
}
