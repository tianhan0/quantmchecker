/**
 * Copyright (c) 2004-2011 QOS.ch
 * All rights reserved.
 *
 * Permission is hereby granted, free  of charge, to any person obtaining
 * a  copy  of this  software  and  associated  documentation files  (the
 * "Software"), to  deal in  the Software without  restriction, including
 * without limitation  the rights to  use, copy, modify,  merge, publish,
 * distribute,  sublicense, and/or sell  copies of  the Software,  and to
 * permit persons to whom the Software  is furnished to do so, subject to
 * the following conditions:
 *
 * The  above  copyright  notice  and  this permission  notice  shall  be
 * included in all copies or substantial portions of the Software.
 *
 * THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
 * EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
 * MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package airplan_1.edu.cyberapex.record.helpers;

import airplan_1.edu.cyberapex.record.ILoggerFactory;
import airplan_1.edu.cyberapex.record.Logger;
import airplan_1.edu.cyberapex.record.event.SubstituteLoggingEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * SubstituteLoggerFactory manages instances of {@link SubstituteLogger}.
 *
 * @author Ceki G&uuml;lc&uuml;
 * @author Chetan Mehrotra
 */
public class SubstituteLoggerFactory implements ILoggerFactory {

    final ConcurrentMap<String, SubstituteLogger> loggers = new ConcurrentHashMap<String, SubstituteLogger>();

    final List<SubstituteLoggingEvent> eventList = Collections.synchronizedList(new ArrayList<SubstituteLoggingEvent>());

    public Logger getLogger(String name) {
        SubstituteLogger logger = loggers.get(name);
        if (logger == null) {
            logger = new SubstituteLoggerBuilder().defineName(name).defineEventList(eventList).generateSubstituteLogger();
            SubstituteLogger oldLogger = loggers.putIfAbsent(name, logger);
            if (oldLogger != null)
                logger = oldLogger;
        }
        return logger;
    }

    public List<String> pullLoggerNames() {
        return new ArrayList<String>(loggers.keySet());
    }

    public List<SubstituteLogger> takeLoggers() {
        return new ArrayList<SubstituteLogger>(loggers.values());
    }

    public List<SubstituteLoggingEvent> getEventList() {
        return eventList;
    }

    public void clear() {
        loggers.clear();
        eventList.clear();
    }
}