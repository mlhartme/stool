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
import net.oneandone.stool.util.Logging;
import net.oneandone.stool.util.Session;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;

@RestController
@RequestMapping("/error")
public class ErrorCollector {
    @Autowired
    private Session session;

    @Autowired
    private Logging logging;

    private Logger log;

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public ResponseEntity collect(@RequestParam String payload) throws MalformedURLException {
        if (log == null) {
            log = logging.errorTool(session.stoolConfiguration.errorTool);
        }
        log.error(payload);
        return new ResponseEntity(HttpStatus.OK);
    }

}

